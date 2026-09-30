"""
RAG 챗봇 API 서버 (FastAPI)

실행
- uvicorn rag_server:app --host 0.0.0.0 --port 8000

(선택) 외부 접속이 필요할 때 ngrok으로 공개
- ngrok http --url=YOUR_URL.ngrok-free.dev 8000
"""

import os
import re
import json
import logging
import time
from datetime import datetime, timedelta, timezone
from difflib import SequenceMatcher

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from dotenv import load_dotenv
from google import genai
from langchain_huggingface import HuggingFaceEmbeddings
from langchain_chroma import Chroma

# ================================================================
# 설정값
# ================================================================

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("rag-server")

load_dotenv()

MAX_LEN = 500
GEMINI_MODEL = os.getenv("GEMINI_MODEL", "gemini-3.1-flash-lite")
# 과부하(503) 시 재시도 간격(초). 총 대기 최대 = 합계
GEMINI_RETRY_DELAYS = [1, 2, 4]
# 재시도 후에도 실패하면 쓸 보조 모델 (.env에 GEMINI_FALLBACK_MODEL=모델명). 비워두면 사용 안 함
GEMINI_FALLBACK_MODEL = os.getenv("GEMINI_FALLBACK_MODEL", "")
# 응답 속도: 단계별 모델 내부 추론(thinking) 수준. 낮을수록 빠름. 비워두면 모델 기본값.
# 모델이 지원하지 않으면 자동으로 설정 없이 다시 호출함.
# - 질문 이해(오타 교정·후속 질문 완성·유형 분류): 단순 변환이라 낮춰도 영향이 작음 → 기본 low
# - 판단+답변(자료 선택·답변 작성): 환각 방지 핵심이라 모델 기본값 유지
GEMINI_THINKING_UNDERSTAND = os.getenv("GEMINI_THINKING_UNDERSTAND", "low")
GEMINI_THINKING_ANSWER = os.getenv("GEMINI_THINKING_ANSWER", "")

# 답변 캐시: 같은 '이해한 질문'이 다시 오면 저장된 답을 바로 반환
CACHE_TTL_SECONDS = 24 * 60 * 60   # 답 하나당 최대 보관 시간 (24시간)
CACHE_MAX_ITEMS = 1000
CACHE_RESET_HOUR = 4               # 매일 이 시각(한국시간)에 캐시 전체 비움. 문서 갱신 시각에 맞추면 좋음

# 질문 이해 프롬프트에 넣을 DB 용어 최대 개수 (질문·대화와 관련 있는 용어만 추려서 넣음)
PROMPT_TERM_LIMIT = 30

# 레벨별 임계값 (거리: 낮을수록 유사)
# 연구 값(0.35 / 0.32 / 0.30 / 0.35)에서 각 0.10씩 완화한 값.
# 서비스 입력은 구어체·오타·후속 질문 등으로 같은 문서도 점수가 흔들려서, 후보 수집 단계에서는 넓게 받고
# 최종 채택은 4단계 LLM 근거 선택 + 코드 검증이 맡는다.
LEVEL_THRESHOLDS = {
    1: 0.45,  # source + category + sub_category
    2: 0.42,  # source + category
    3: 0.40,  # source
    4: 0.45,  # 필터 없음 (전체 DB 대상 안전망)
}
EARLY_STOP_SCORE = 0.15

SEARCH_K = 5          # 검색 질문 1개·레벨 1개당 가져올 문서 수
CANDIDATE_K = 5       # LLM에 넘길 최종 후보 수

# 빠른 경로: 이전 대화가 없고 질문에 DB 용어가 그대로 들어 있으면 '질문 이해' LLM 호출을 건너뜀
# (의도 판단은 4단계 답변 LLM이 자료를 고르면서 함께 하므로 정확도 손실이 적음). False면 항상 호출.
FAST_PATH = True

# 키워드 → DB 용어 매핑 유사도 기준 (0~1, 높을수록 엄격)
KEYWORD_MATCH_RATIO = 0.75

# 대화 맥락: 채팅방 전체 대화를 받되, 토큰 절약을 위해 아래 규칙으로 압축
MAX_QUESTION_CHARS = 200        # 사용자 질문 1개당 최대 글자수
RECENT_ANSWER_COUNT = 2         # 챗봇 답변은 최근 N개만 포함
MAX_ANSWER_CHARS = 300          # 챗봇 답변 1개당 최대 글자수
MAX_HISTORY_TOTAL_CHARS = 4000  # 전체 대화 텍스트 상한 (넘으면 오래된 것부터 제외)

REFUSAL_MSG = "죄송합니다. 제공된 문서 내에서 해당 질문에 대한 답변을 찾을 수 없습니다."

# EC2(리눅스, GPU 없음)에서는 반드시 cpu 로 설정하세요.
# NVIDIA GPU가 붙은 인스턴스(g4dn 등)라면 cuda 로 변경 가능합니다.
EMBEDDING_DEVICE = os.getenv("EMBEDDING_DEVICE", "cpu")
CHROMA_DB_PATH = os.getenv("CHROMA_DB_PATH", "./chroma_db")

api_key = os.getenv("GEMINI_API_KEY")
if not api_key:
    raise RuntimeError("GEMINI_API_KEY 환경변수가 설정되지 않았습니다.")

client = genai.Client(api_key=api_key)

# ================================================================
# 초기화 & DB 메타데이터 Unique 목록 수집 (서버 시작 시 1회)
# ================================================================

logger.info(f"임베딩 모델 로드 중... (device={EMBEDDING_DEVICE})")
embeddings = HuggingFaceEmbeddings(
    model_name="BAAI/bge-m3",
    model_kwargs={"device": EMBEDDING_DEVICE},
    encode_kwargs={"normalize_embeddings": True},
)

logger.info(f"Chroma DB 로드 중... (path={CHROMA_DB_PATH})")
vectorstore = Chroma(persist_directory=CHROMA_DB_PATH, embedding_function=embeddings)

all_metadatas = vectorstore.get(include=["metadatas"])["metadatas"]


def _unique_sorted(key: str) -> list[str]:
    # 긴 문자열부터 매칭해야 부분 일치 오매칭을 줄일 수 있음
    return sorted({m.get(key) for m in all_metadatas if m and m.get(key)}, key=len, reverse=True)


db_sources = _unique_sorted("source")
db_categories = _unique_sorted("category")
db_sub_categories = _unique_sorted("sub_category")

# DB 용어 전체 (기관명 + 카테고리 + 서브카테고리)
db_terms = sorted(set(db_categories) | set(db_sub_categories), key=len, reverse=True)
db_all_terms = sorted(set(db_sources) | set(db_terms), key=len, reverse=True)

# category / sub_category → 소속 source 역매핑 (질문에 기관명이 없어도 필터를 좁히기 위함)
category_to_sources: dict[str, set] = {}
sub_to_parents: dict[str, set] = {}
for _m in all_metadatas:
    if not _m or not _m.get("source"):
        continue
    if _m.get("category"):
        category_to_sources.setdefault(_m["category"], set()).add(_m["source"])
        if _m.get("sub_category"):
            sub_to_parents.setdefault(_m["sub_category"], set()).add((_m["source"], _m["category"]))

logger.info(
    f"DB 메타데이터 로드 완료 - Source: {len(db_sources)}개, "
    f"Category: {len(db_categories)}개, Sub-Category: {len(db_sub_categories)}개"
)


# ================================================================
# 0단계. 규칙 기반 정제 (LLM 없음)
# ================================================================

def _nospace(s: str) -> str:
    return re.sub(r"\s+", "", s or "")


# DB 키워드의 띄어쓰기 변형을 잡는 정규식 (글자 사이 공백 허용, DB 메타데이터에서 자동 생성)
_KEYWORD_PATTERNS = [
    (re.compile(r"\s*".join(map(re.escape, _nospace(term)))), term)
    for term in sorted(db_all_terms, key=lambda x: len(_nospace(x)), reverse=True)
    if len(_nospace(term)) >= 2
]


def normalize_keywords(text: str) -> str:
    """질문 속 DB 키워드의 띄어쓰기 변형을 DB 표기로 통일"""
    for pattern, term in _KEYWORD_PATTERNS:
        text = pattern.sub(term, text)
    return text


def pre_clean_question(text: str) -> str:
    """낱자모, 반복 기호, 공백 정리 + DB 키워드 띄어쓰기 통일"""
    text = re.sub(r"[ㄱ-ㅎㅏ-ㅣ]+", "", text)
    text = re.sub(r"([?!.~])\1+", r"\1", text)
    text = re.sub(r"\s+", " ", text).strip()
    return normalize_keywords(text)


def clean_answer(raw_answer: str) -> str:
    """LLM 답변 후처리: 태그·서두 거절 문구 제거, [NO_DATA]/빈 값이면 공식 거절 문구"""
    text = (raw_answer or "").strip()
    text = re.sub(r"^\s*\[AI\s*최종\s*답변\]\s*:?\s*", "", text)
    text = re.sub(r"^\s*\[답변\]\s*:?\s*", "", text).strip()

    if "[NO_DATA]" in text or not text:
        return REFUSAL_MSG

    for prefix in (REFUSAL_MSG, "제공된 문서 내에서 해당 질문에 대한 답변을 찾을 수 없습니다."):
        if text.startswith(prefix):
            text = text[len(prefix):].strip()

    if text.startswith("죄송합니다.") or text.startswith("죄송합니다,"):
        text = text[len("죄송합니다."):].strip()

    return text or REFUSAL_MSG


# ================================================================
# 대화 기록 정리
# ================================================================

def format_history(history) -> str:
    """
    채팅방 전체 대화를 '사용자: ... / 챗봇: ...' 텍스트로 변환
    - 사용자 질문: 전부 포함 (대화 흐름 파악용)
    - 챗봇 답변: 최근 RECENT_ANSWER_COUNT개만 포함, 거절 답변은 제외
    - 전체 길이가 상한을 넘으면 오래된 대화부터 제외
    """
    turns = [t for t in (history or []) if (t.content or "").strip()]

    answer_idx = [i for i, t in enumerate(turns) if t.role != "user" and t.content.strip() != REFUSAL_MSG]
    keep_answers = set(answer_idx[-RECENT_ANSWER_COUNT:])

    lines = []
    for i, turn in enumerate(turns):
        content = re.sub(r"\s+", " ", turn.content).strip()
        if turn.role == "user":
            limit, speaker = MAX_QUESTION_CHARS, "사용자"
        elif i in keep_answers:
            limit, speaker = MAX_ANSWER_CHARS, "챗봇"
        else:
            continue
        if len(content) > limit:
            content = content[:limit] + "..."
        lines.append(f"{speaker}: {content}")

    kept, total = [], 0
    for line in reversed(lines):
        if total + len(line) > MAX_HISTORY_TOTAL_CHARS:
            break
        kept.append(line)
        total += len(line) + 1
    return "\n".join(reversed(kept))


# ================================================================
# 1단계. 질문 이해 (LLM)
# ================================================================

def _bigrams(s: str) -> set:
    s = _nospace(s)
    return {s[i:i + 2] for i in range(len(s) - 1)}


def select_prompt_terms(question: str, history_text: str, limit: int = PROMPT_TERM_LIMIT) -> list[str]:
    """
    DB 용어 전체 대신, 질문·최근 대화와 글자(2글자 단위)가 겹치는 용어만 추려서 프롬프트에 넣음
    (입력 길이를 줄여 응답 속도 개선. 실제 매핑은 2단계에서 코드가 전체 목록으로 하므로 누락 영향 적음)
    """
    text_grams = _bigrams(question) | _bigrams(history_text[-1000:])
    scored = []
    for term in db_all_terms:
        g = _bigrams(term)
        if not g:
            continue
        overlap = len(g & text_grams) / len(g)
        if overlap > 0:
            scored.append((overlap, term))
    scored.sort(key=lambda x: -x[0])
    return [t for _, t in scored[:limit]]


def build_understand_prompt(question: str, history_text: str) -> str:
    terms = select_prompt_terms(question, history_text)
    return f"""너는 기관 FAQ 챗봇의 질문 이해 담당이야.
사용자 메시지를 읽고, 사용자가 실제로 알고 싶은 것을 파악해서 문서 검색에 쓸 수 있게 정리해줘.
정해진 유형이나 패턴에 끼워 맞추지 말고, 사용자가 실제로 쓴 말과 대화 흐름에서만 의도를 읽어내.

[할 일]
1. request_type (문구가 아니라 의미로 판단해. 오타·줄임말·구어체여도 뜻이 같으면 같은 유형이야)
 - "repeat": 특정 주제를 지정하지 않고 '다시' 알려 달라는 요청은 모두 repeat이야. 기준은 항상 '직전 질문'이야.
   (다시 알려줘, 뭐라고?, 못 봤어, 한 번 더, 방금 뭐라고 했어 등)
 - "conversation": 문서 내용이 아니라 이 대화 자체에 대한 질문 (앞서 무엇을 물었는지, 몇 번째로 무엇을 물었는지 등)
 - "search": 그 외 모든 경우.
   다시 달라는 요청이라도 특정 주제·예전 질문을 지정한 경우(예: 처음에 물어본 그거), 더 자세히·쉽게·다른 측면으로
   설명해 달라는 요청은 "search"로 하고, 지정된 그 주제로 standalone_question을 만들어.

2. standalone_question
 - 오타와 맞춤법을 교정하고, 이전 대화 없이도 뜻이 통하는 자연스러운 질문 문장 1개로 만들어.
 - 현재 메시지가 지시어를 쓰거나 대상을 생략했으면 [이전 대화]에서 대상을 가져와 채워. 여러 주제가 있으면 가장 최근 주제를 우선해.
 - repeat이면 standalone_question은 [이전 대화]의 '직전 사용자 질문'을 교정한 문장으로 써.
 - 현재 메시지에 대상이 분명히 있으면 [이전 대화]는 무시해.
 - 사용자가 쓴 표현의 의미를 그대로 살려. 다른 의미의 단어로 바꾸거나 범위를 좁히거나 넓히지 마.
 - 현재 메시지와 [이전 대화]에 없는 대상·조건·날짜·수치는 절대 추가하지 마.

3. keywords
 - 사용자가 묻는 대상을 나타내는 핵심 명사들 (조사·서술어 제외, 오타 교정).
 - [DB 용어 목록]에 같은 뜻의 용어가 있으면 목록의 표기를 써. 없으면 사용자 표기를 써.

[DB 용어 목록]
{terms if terms else "(관련 용어 없음)"}

반드시 아래 JSON 형식으로만 답해.
{{"request_type": "search 또는 repeat 또는 conversation", "standalone_question": "질문 문장", "keywords": ["키워드1", "키워드2"]}}

[이전 대화]
{history_text or "(없음)"}

[현재 사용자 메시지]
{question}"""


def understand_question(user_input: str, history_text: str) -> dict:
    """
    Returns: {"request_type", "standalone_question", "keywords", "cleaned"}
    - 실패하면 규칙 정제 결과만으로 동작 (연구와 동일한 원문 기반 검색)
    """
    cleaned = pre_clean_question(user_input) or user_input
    fallback = {"request_type": "search", "standalone_question": cleaned, "keywords": [], "cleaned": cleaned}

    try:
        raw = call_gemini(build_understand_prompt(cleaned, history_text), json_mode=True,
                          thinking_level=GEMINI_THINKING_UNDERSTAND)
        parsed = json.loads(raw.strip().replace("```json", "").replace("```", ""))
    except HTTPException as e:
        logger.warning(f"질문 이해 LLM 실패 → 규칙 정제 결과 사용 ({e.detail})")
        return fallback
    except Exception:
        logger.warning("질문 이해 JSON 파싱 실패 → 규칙 정제 결과 사용")
        return fallback

    request_type = parsed.get("request_type")
    if request_type not in ("repeat", "conversation", "search") or not history_text:
        request_type = "search"   # 이전 대화가 없으면 되돌아볼 내용이 없으므로 검색

    standalone = str(parsed.get("standalone_question") or "").strip()
    # 안전장치: 비었거나 지나치게 길어지면(정보 추가 의심) 원문 사용
    if not standalone or len(standalone) > max(len(cleaned), 30) * 2 + 30:
        standalone = cleaned

    keywords = parsed.get("keywords") or []
    if not isinstance(keywords, list):
        keywords = []
    keywords = [str(k).strip() for k in keywords if str(k).strip()]

    return {
        "request_type": request_type,
        "standalone_question": normalize_keywords(standalone),
        "keywords": keywords,
        "cleaned": cleaned,
    }


def fast_path_understand(user_input: str, history_text: str) -> dict | None:
    """
    이전 대화가 없고(채팅방 첫 질문), 규칙 정제한 질문에 DB 용어가 그대로 들어 있으면
    LLM 없이 바로 검색으로 진행. 이전 대화가 있으면 후속 질문일 수 있으므로 항상 LLM으로 이해.
    """
    if history_text:
        return None
    cleaned = pre_clean_question(user_input) or user_input
    c = _nospace(cleaned)
    terms = [t for t in db_all_terms if len(_nospace(t)) >= 2 and _nospace(t) in c]
    if not terms:
        return None
    return {"request_type": "search", "standalone_question": cleaned, "keywords": terms, "cleaned": cleaned}


# ================================================================
# 2단계. 키워드 → DB 용어 매핑 (규칙, 문자열 유사도)
# ================================================================

def map_keyword_to_db_term(keyword: str) -> str | None:
    """
    키워드를 DB 용어 중 가장 비슷한 것에 매핑 (오타·띄어쓰기·표기 차이 흡수)
    - 완전 일치(띄어쓰기 무시) → 바로 매핑
    - 줄임말/부분 표기: 키워드를 포함하는 DB 용어가 딱 하나면 매핑 (예: 줄여 부른 기관명 → 정식 명칭)
      여러 개면 어느 것인지 알 수 없으므로 매핑하지 않음
    - 긴 표기: 키워드 안에 DB 용어가 들어 있으면 그 용어로 매핑 (가장 긴 것)
    - 그 외: 문자열 유사도가 KEYWORD_MATCH_RATIO 이상인 것 중 최고값 (오타 흡수)
    - 기준 미만이면 매핑하지 않음 (억지로 엉뚱한 용어에 붙이지 않음)
    """
    k = _nospace(keyword)
    if len(k) < 2:
        return None

    for term in db_all_terms:
        if k == _nospace(term):
            return term

    containing = [t for t in db_all_terms if k in _nospace(t)]
    if len(containing) == 1:
        return containing[0]
    if len(containing) > 1:
        # 여러 용어에 걸쳐도, 그 용어들이 모두 한 기관에 속하면 그 기관으로 매핑
        # (예: 기관명 일부로 부른 경우 — 기관명 자체와 그 기관의 카테고리들이 함께 걸리는 상황)
        owners = set()
        for t in containing:
            if t in db_sources:
                owners.add(t)
            owners |= category_to_sources.get(t, set())
            owners |= {src for src, _ in sub_to_parents.get(t, set())}
        if len(owners) == 1:
            return next(iter(owners))
        return None   # 여러 기관에 걸치는 일반 단어 → 어느 것인지 모르므로 매핑 안 함

    contained = [t for t in db_all_terms if len(_nospace(t)) >= 2 and _nospace(t) in k]
    if contained:
        return max(contained, key=lambda t: len(_nospace(t)))

    best_term, best_ratio = None, 0.0
    for term in db_all_terms:
        t = _nospace(term)
        ratio = SequenceMatcher(None, k, t).ratio()
        if ratio > best_ratio:
            best_term, best_ratio = term, ratio

    return best_term if best_ratio >= KEYWORD_MATCH_RATIO else None


def extract_metadata_filter(text: str) -> dict | None:
    """
    텍스트 속 DB 용어로 메타데이터 필터 생성 (띄어쓰기 무시)
    - 기관명이 없어도 category/sub_category가 한 기관에만 속하면 그 기관으로 역추론
    """
    t = _nospace(text)
    matched_source = next((s for s in db_sources if _nospace(s) in t), None)
    matched_category = next((c for c in db_categories if _nospace(c) in t), None)
    matched_sub = next((sc for sc in db_sub_categories if _nospace(sc) in t), None)

    if matched_sub and not matched_category:
        parents = sub_to_parents.get(matched_sub, set())
        if matched_source:
            parents = {p for p in parents if p[0] == matched_source}
        if len(parents) == 1:
            matched_source, matched_category = next(iter(parents))

    if matched_category and not matched_source:
        sources = category_to_sources.get(matched_category, set())
        if len(sources) == 1:
            matched_source = next(iter(sources))

    if not matched_source:
        return None

    if matched_category and matched_source not in category_to_sources.get(matched_category, set()):
        matched_category, matched_sub = None, None
    if matched_sub and (matched_source, matched_category) not in sub_to_parents.get(matched_sub, set()):
        matched_sub = None

    filt = {"source": matched_source}
    if matched_category:
        filt["category"] = matched_category
        if matched_sub:
            filt["sub_category"] = matched_sub
    return filt


# ================================================================
# 3단계. 후보 검색 (계층적 검색 + 여유 임계값, 검색 질문 2개)
# ================================================================

def build_levels(metadata_filter: dict | None) -> list[tuple[int, dict | None]]:
    source = metadata_filter.get("source") if metadata_filter else None
    category = metadata_filter.get("category") if metadata_filter else None
    sub_category = metadata_filter.get("sub_category") if metadata_filter else None

    levels: list[tuple[int, dict | None]] = []
    if source and category and sub_category:
        levels.append((1, {"$and": [
            {"source": {"$eq": source}},
            {"category": {"$eq": category}},
            {"sub_category": {"$eq": sub_category}},
        ]}))
    if source and category:
        levels.append((2, {"$and": [
            {"source": {"$eq": source}},
            {"category": {"$eq": category}},
        ]}))
    if source:
        levels.append((3, {"source": {"$eq": source}}))
    levels.append((4, None))
    return levels


def hierarchical_retrieval(query_vec: list[float], metadata_filter: dict | None, k: int = SEARCH_K):
    """
    검색 질문 1개(임베딩 벡터)에 대한 계층적 검색. 모든 레벨에서 임계값을 통과한 문서를 모음.
    Returns: [(doc, score, level)]
    """
    collected = []

    for level_num, filt in build_levels(metadata_filter):
        threshold = LEVEL_THRESHOLDS[level_num]
        try:
            # langchain_chroma: 반환 점수는 거리(distance) — 낮을수록 유사
            results = vectorstore.similarity_search_by_vector_with_relevance_scores(
                embedding=query_vec, k=k, filter=filt
            )
            if not results:
                continue

            valid = [(doc, score) for doc, score in results if score < threshold]
            min_score = min(score for _, score in results)
            logger.info(
                f"    [Level {level_num}] 임계치 {threshold:.2f} 통과 {len(valid)}/{len(results)}개 "
                f"(최상위: {min_score:.4f})"
            )
            collected.extend((doc, score, level_num) for doc, score in valid)

            if valid and min_score < EARLY_STOP_SCORE:
                break

        except Exception as e:
            logger.error(f"    [Error] Level {level_num} 검색 예외: {e}")

    return collected


def gather_candidates(queries: list[str], metadata_filter: dict | None) -> list[tuple]:
    """여러 검색 질문의 결과를 합쳐 중복 제거 → 점수 순 상위 CANDIDATE_K개"""
    merged: dict[str, tuple] = {}
    # 검색 질문들을 한 번에 임베딩 (CPU에서 질문마다 따로 하는 것보다 빠름)
    vectors = embeddings.embed_documents(queries) if len(queries) > 1 else [embeddings.embed_query(queries[0])]
    for q, vec in zip(queries, vectors):
        logger.info(f"  검색 질문: '{q}'")
        for doc, score, level in hierarchical_retrieval(vec, metadata_filter):
            key = getattr(doc, "id", None) or doc.page_content
            if key not in merged or score < merged[key][1]:
                merged[key] = (doc, score, level)
    return sorted(merged.values(), key=lambda x: x[1])[:CANDIDATE_K]


# ================================================================
# 4단계. 판단 + 답변 (LLM, JSON)
# ================================================================

def build_answer_prompt(candidates: list[tuple], user_input: str, standalone: str, history_text: str) -> str:
    blocks = []
    for i, (doc, _, _) in enumerate(candidates, start=1):
        md = doc.metadata or {}
        header = f"[자료 {i}] (출처: {md.get('source', '-')} / {md.get('category', '-')} > {md.get('sub_category', '-')})"
        blocks.append(f"{header}\n{doc.page_content.strip()}")
    context_text = "\n\n".join(blocks)

    return f"""너는 제공된 [참고자료]만을 근거로 답하는 기관 FAQ 챗봇이야.
사용자와 자연스럽게 대화하되, 자료에 없는 내용은 절대 지어내지 않아.

[참고자료]
{context_text}

[이전 대화]
{history_text or "(없음)"}

[사용자 메시지]
{user_input}

[이해한 질문]
{standalone}

[할 일]
1. 자료 선택 (used_docs)
 - 각 자료가 [이해한 질문]과 '같은 대상'에 대해 '사용자가 알고 싶은 정보'를 담고 있는지 판단해.
 - 표현이 달라도 의미가 같으면 관련 있는 자료야.
 - 이름이 비슷해도 다른 대상(다른 제도·서비스·기관)을 다루거나, 같은 대상이라도 다른 정보만 담은 자료는 고르지 마.
 - 관련 있는 자료가 하나도 없으면 used_docs는 빈 배열 []로 하고 answer도 빈 문자열로 둬.

2. 답변 (answer)
 - used_docs로 고른 자료의 내용만 사용해서 [이해한 질문]에 답해.
 - 자료에 없는 수치·날짜·금액·연락처·절차·자격조건은 절대 추측하거나 일반 상식으로 채우지 마.
 - 질문의 일부만 자료에 있으면 있는 부분만 답하고, 확인되지 않는 부분은 "제공된 문서에서 확인되지 않습니다"라고 짧게 밝혀.
 - '중단됨', '제외됨', '불가능함' 같은 부정적 사실도 올바른 답이니 사과 없이 그대로 전달해.
 - 사용자가 이전 내용을 다시 설명해 달라고 하면 자료를 바탕으로 처음부터 다시 완전하게 답해.
 - [이전 대화]는 질문을 이해하는 데만 쓰고, 답의 근거로 쓰지 마.
 - 인사말, "자료에 따르면", 자료 번호 언급 없이 바로 핵심부터 자연스러운 문장으로 답해.
   절차나 조건이 여러 개면 짧은 목록으로 정리하고, 공백 포함 1,000자 이내로 써.

반드시 아래 JSON 형식으로만 답해.
{{"used_docs": [1, 2], "answer": "답변 내용"}}"""


def generate_answer(candidates, user_input, standalone, history_text) -> tuple[str, list[int]]:
    """Returns: (답변, 사용한 자료 번호). 고른 자료가 없으면 거절 문구."""
    raw = call_gemini(build_answer_prompt(candidates, user_input, standalone, history_text), json_mode=True,
                      thinking_level=GEMINI_THINKING_ANSWER)
    try:
        parsed = json.loads(raw.strip().replace("```json", "").replace("```", ""))
    except Exception:
        logger.warning(f"답변 JSON 파싱 실패 → 거절 (raw={raw[:200]!r})")
        return REFUSAL_MSG, []

    used = []
    for d in parsed.get("used_docs") or []:
        try:
            d = int(d)
        except (TypeError, ValueError):
            continue
        if 1 <= d <= len(candidates) and d not in used:
            used.append(d)

    answer = str(parsed.get("answer") or "").strip()

    # 근거 자료가 없는데 답이 있으면 신뢰하지 않음 → 거절 (환각 2차 방어)
    if not used or not answer:
        return REFUSAL_MSG, []

    return clean_answer(answer), used


# ================================================================
# 답변 캐시 (메모리, 서버 프로세스별)
# - 키: '이해한 질문'(띄어쓰기·문장부호 무시). 이해한 질문은 이전 대화 없이도 뜻이 통하는 문장이라
#   다른 대화와 섞이지 않음
# - 정상 답변만 저장 (거절은 저장하지 않음 → 일시적 문제로 거절된 답이 굳지 않게)
# - 매일 CACHE_RESET_HOUR(한국시간)에 전체 초기화, 서버 재시작 시에도 비워짐
# ================================================================

_answer_cache: dict[str, tuple[float, str]] = {}
_KST = timezone(timedelta(hours=9))
_cache_day = None   # 마지막으로 캐시를 비운 '하루'의 기준 날짜


def _reset_cache_if_new_day():
    """매일 CACHE_RESET_HOUR(한국시간)가 지나면 캐시 전체 비움 (별도 스케줄러 없이 요청 때 확인)"""
    global _cache_day
    day = (datetime.now(_KST) - timedelta(hours=CACHE_RESET_HOUR)).date()
    if _cache_day != day:
        if _cache_day is not None and _answer_cache:
            logger.info(f"하루 단위 캐시 초기화 ({len(_answer_cache)}개 삭제)")
            _answer_cache.clear()
        _cache_day = day


def _cache_key(question: str) -> str:
    return re.sub(r"[\s?.!~,]+", "", question or "").lower()


def cache_get(question: str) -> str | None:
    _reset_cache_if_new_day()
    key = _cache_key(question)
    item = _answer_cache.get(key)
    if not item:
        return None
    saved_at, answer = item
    if time.time() - saved_at > CACHE_TTL_SECONDS:
        _answer_cache.pop(key, None)
        return None
    return answer


def cache_set(question: str, answer: str):
    _reset_cache_if_new_day()
    if not answer or answer == REFUSAL_MSG:
        return
    if len(_answer_cache) >= CACHE_MAX_ITEMS:
        oldest = min(_answer_cache, key=lambda k: _answer_cache[k][0])
        _answer_cache.pop(oldest, None)
    _answer_cache[_cache_key(question)] = (time.time(), answer)


# ================================================================
# 직전 답변 다시 보기 (repeat)
# ================================================================

def get_last_answer(history) -> str | None:
    """history에서 가장 최근 챗봇 답변 원문 (자르지 않음). 거절 답변이면 None → 검색으로 대체."""
    turns = [t for t in (history or []) if (t.content or "").strip()]
    # 마지막 항목이 사용자 질문이면 직전 질문에 답변이 없었던 것(생성 중단 등) → 더 예전 답을 가져오지 않음
    if not turns or turns[-1].role == "user":
        return None
    content = turns[-1].content.strip()
    return None if content == REFUSAL_MSG else content


def get_last_question(history) -> str | None:
    """history에서 가장 최근 사용자 질문 원문"""
    for turn in reversed(history or []):
        if turn.role == "user" and (turn.content or "").strip():
            return turn.content.strip()
    return None


# ================================================================
# 대화 자체에 대한 질문 (문서 검색 없음)
# ================================================================

def build_conversation_prompt(user_input: str, history_text: str) -> str:
    return f"""너는 기관 문서 기반 챗봇이야. 사용자가 이 대화 자체에 대해 묻고 있어.
아래 [이전 대화]만을 근거로 답해.

[지침]
1. [이전 대화]에 실제로 있는 내용만 사용해. 없는 내용은 추측하지 마.
2. 사용자가 이전에 한 질문을 물으면 질문들을 순서대로 정리해서 알려줘.
3. [이전 대화]에서 답을 찾을 수 없으면 다른 말 없이 오직 '[NO_DATA]' 라고만 출력해.
4. 인사말이나 사족 없이 바로 답해. 공백 포함 1,000자 이내.

[이전 대화]
{history_text}

[사용자 질문]
{user_input}

[답변]:"""


# ================================================================
# Gemini 호출
# ================================================================

def _is_retryable(error_str: str) -> bool:
    """일시적 오류(과부하·분당 한도·서버 오류)만 재시도. 일일 한도 초과는 재시도해도 소용없음."""
    if "PerDay" in error_str or "per_day" in error_str.lower():
        return False
    return any(code in error_str for code in ("503", "500", "429", "UNAVAILABLE", "RESOURCE_EXHAUSTED"))


def _generate(model: str, prompt: str, config: dict) -> str:
    start = time.perf_counter()
    response = client.models.generate_content(model=model, contents=prompt, config=config)
    thinking = (config.get("thinking_config") or {}).get("thinking_level", "기본")
    logger.info(
        f"    Gemini 호출 {time.perf_counter() - start:.2f}s "
        f"(모델={model}, 추론={thinking}, 프롬프트 {len(prompt)}자)"
    )
    return response.text or ""


_thinking_unsupported = False


def _disable_thinking():
    global _thinking_unsupported
    if not _thinking_unsupported:
        logger.warning("현재 모델이 thinking_level 설정을 지원하지 않아 해제합니다.")
    _thinking_unsupported = True


def call_gemini(prompt: str, json_mode: bool = False, thinking_level: str = "") -> str:
    """
    Gemini 호출 + 일시적 오류 재시도
    - 503(과부하)·429(분당 한도)·500이면 GEMINI_RETRY_DELAYS 간격으로 재시도
    - 그래도 실패하고 GEMINI_FALLBACK_MODEL이 설정돼 있으면 그 모델로 한 번 더 시도
    """
    config = {
        "temperature": 0,
        # 함수 호출 기능을 쓰지 않으므로 끔 ('AFC is enabled' 로그 제거)
        "automatic_function_calling": {"disable": True},
    }
    if json_mode:
        config["response_mime_type"] = "application/json"
    if thinking_level and not _thinking_unsupported:
        config["thinking_config"] = {"thinking_level": thinking_level}

    last_error = ""
    for attempt, delay in enumerate([0] + GEMINI_RETRY_DELAYS):
        if delay:
            logger.warning(f"Gemini 재시도 {attempt}/{len(GEMINI_RETRY_DELAYS)} ({delay}초 후)")
            time.sleep(delay)
        try:
            return _generate(GEMINI_MODEL, prompt, config)
        except Exception as e:
            last_error = str(e)
            logger.error(f"Gemini 호출 실패: {last_error[:200]}")
            # 모델이 thinking 설정을 지원하지 않으면 설정을 빼고 즉시 다시 호출
            if "thinking_config" in config and "thinking" in last_error.lower():
                _disable_thinking()
                config.pop("thinking_config", None)
                try:
                    return _generate(GEMINI_MODEL, prompt, config)
                except Exception as e2:
                    last_error = str(e2)
            if not _is_retryable(last_error):
                break

    if GEMINI_FALLBACK_MODEL and _is_retryable(last_error):
        try:
            logger.warning(f"보조 모델로 전환: {GEMINI_FALLBACK_MODEL}")
            return _generate(GEMINI_FALLBACK_MODEL, prompt, config)
        except Exception as e:
            last_error = str(e)
            logger.error(f"보조 모델 호출 실패: {last_error[:200]}")

    if "429" in last_error:
        if "PerDay" in last_error or "per_day" in last_error.lower():
            raise HTTPException(status_code=429, detail="일일 요청 한도를 초과했습니다. 내일 오전 9시 이후 다시 이용해주세요.")
        raise HTTPException(status_code=429, detail="잠시 요청이 많습니다. 잠시 후 다시 시도해주세요.")
    if "503" in last_error or "UNAVAILABLE" in last_error:
        raise HTTPException(status_code=503, detail="서버가 혼잡합니다. 잠시 후 다시 시도해주세요.")
    raise HTTPException(status_code=500, detail="오류가 발생했습니다. 잠시 후 다시 시도해주세요.")


# ================================================================
# FastAPI 앱 & 엔드포인트
# ================================================================

app = FastAPI(title="RAG Chat Server")


class ChatTurn(BaseModel):
    role: str      # "user" 또는 "assistant"
    content: str


class QueryRequest(BaseModel):
    question: str
    history: list[ChatTurn] = []   # 같은 채팅방의 이전 대화 (오래된 것 → 최근 순)


class QueryResponse(BaseModel):
    answer: str


def _fmt_timings(timings: dict, t0: float) -> str:
    parts = " / ".join(f"{k} {v:.2f}s" for k, v in timings.items())
    return f"{parts} / 총 {time.perf_counter() - t0:.2f}s"


@app.get("/health")
def health_check():
    return {"status": "ok", "cache_items": len(_answer_cache)}


@app.post("/rag/query", response_model=QueryResponse)
def query(request: QueryRequest):
    user_input = request.question.strip()

    if not user_input:
        raise HTTPException(status_code=400, detail="질문이 비어있습니다.")

    if len(user_input) > MAX_LEN:
        raise HTTPException(
            status_code=400,
            detail=f"입력 가능한 글자수를 초과했습니다. ({len(user_input)}/{MAX_LEN}자)",
        )

    t0 = time.perf_counter()
    timings = {}
    history_text = format_history(request.history)

    # 1단계. 질문 이해 (빠른 경로면 LLM 생략)
    u = fast_path_understand(user_input, history_text) if FAST_PATH else None
    if u:
        logger.info("빠른 경로: 질문 이해 LLM 생략")
    else:
        u = understand_question(user_input, history_text)
    timings["이해"] = time.perf_counter() - t0
    logger.info(
        f"원문='{user_input[:40]}' → 이해한 질문='{u['standalone_question']}' "
        f"(유형={u['request_type']}, 키워드={u['keywords']})"
    )

    # 직전 답변 다시 보기 → 저장된 원문 그대로 (없거나 거절이었으면 아래 검색으로 진행)
    if u["request_type"] == "repeat":
        last_answer = get_last_answer(request.history)
        if last_answer:
            logger.info(f"직전 답변 원문 반환 (repeat) | 총 {time.perf_counter() - t0:.2f}s")
            return QueryResponse(answer=last_answer)
        # 직전 답변이 없거나 거절이었으면 → 직전 질문으로 다시 검색
        last_q = get_last_question(request.history)
        if last_q:
            u["standalone_question"] = pre_clean_question(last_q) or last_q
            u["cleaned"] = u["standalone_question"]
        logger.info(f"repeat이지만 직전 답변 없음 → 직전 질문으로 다시 검색: '{u['standalone_question']}'")

    # 대화 자체에 대한 질문 → 이전 대화로만 답변
    if u["request_type"] == "conversation":
        raw = call_gemini(build_conversation_prompt(user_input, history_text))
        return QueryResponse(answer=clean_answer(raw))

    # 캐시 확인 (같은 '이해한 질문'이면 저장된 답 바로 반환)
    cached = cache_get(u["standalone_question"])
    if cached:
        logger.info(f"캐시 적중 | {_fmt_timings(timings, t0)}")
        return QueryResponse(answer=cached)

    # 2단계. 키워드 → DB 용어 매핑 → 메타데이터 필터
    mapped_terms = [t for t in (map_keyword_to_db_term(k) for k in u["keywords"]) if t]
    search_filter = extract_metadata_filter(" ".join([u["standalone_question"]] + mapped_terms))
    logger.info(f"매핑된 DB 용어={mapped_terms}, 필터={search_filter}")

    # 3단계. 후보 검색: 이해한 질문 + 원문(질문 이해가 빗나갔을 때의 안전망)
    # 원문이 대상을 직접 담고 있을 때만 추가 ('다시 알려줘' 같은 조각 문장은 검색해도 의미 없음)
    queries = [u["standalone_question"]]
    cleaned_ns = _nospace(u["cleaned"])
    original_has_target = any(_nospace(k) in cleaned_ns for k in (u["keywords"] + mapped_terms) if len(_nospace(k)) >= 2)
    if cleaned_ns != _nospace(u["standalone_question"]) and original_has_target:
        queries.append(u["cleaned"])

    t1 = time.perf_counter()
    candidates = gather_candidates(queries, search_filter)
    timings["검색"] = time.perf_counter() - t1

    if not candidates:
        logger.info(f"후보 문서 없음 → LLM 호출 없이 거절 | {_fmt_timings(timings, t0)}")
        return QueryResponse(answer=REFUSAL_MSG)

    logger.info("후보: " + ", ".join(f"[{i}] {s:.4f}(L{lv})" for i, (_, s, lv) in enumerate(candidates, 1)))

    # 4단계. 판단 + 답변
    t2 = time.perf_counter()
    answer, used = generate_answer(candidates, user_input, u["standalone_question"], history_text)
    timings["답변"] = time.perf_counter() - t2
    logger.info(f"사용 자료={used or '없음 → 거절'} | {_fmt_timings(timings, t0)}")
    cache_set(u["standalone_question"], answer)

    return QueryResponse(answer=answer)