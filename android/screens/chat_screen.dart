import 'package:flutter/material.dart';

class ChatScreen extends StatefulWidget {
  const ChatScreen({super.key});

  @override
  State<ChatScreen> createState() => _ChatScreenState();
}

class _ChatScreenState extends State<ChatScreen> {
  final TextEditingController _messageController = TextEditingController();

  // 1. 사용자와 AI의 채팅을 구분하기 위해 Map 형태로 저장
  // (예: isUser가 true면 사용자 채팅, false면 AI 채팅)
  final List<Map<String, dynamic>> _messages = [];

  // 2. AI가 답변을 고민 중인지 확인하는 '로딩 상태' 스위치
  bool _isWaiting = false;

  // 전송 버튼 누르기
  void _sendMessage() async {
    // 빈칸이거나, 이미 로딩 중이면 아무 작동도 안 함
    if (_messageController.text.trim().isEmpty || _isWaiting) return;

    String text = _messageController.text;

    setState(() {
      // 사용자 말풍선을 리스트에 추가하고 입력창 비우기
      _messages.add({'text': text, 'isUser': true});
      _messageController.clear();
      _isWaiting = true; // "이제 AI가 생각할 시간이야!" 라고 스위치 켜기
    });

    // 5초 동안 AI가 생각하는 척 기다리는 효과 (진짜 서버랑 연결할 땐 이 부분이 바뀜)
    await Future.delayed(const Duration(seconds: 5));

    // 5초가 지났는데도 사용자가 '중지'를 안 눌러서 _isWaiting이 그대로 true라면?
    // -> AI의 답변을 화면에 띄운다
    if (_isWaiting) {
      setState(() {
        _messages.add({'text': '콜핑 AI의 가짜 답변입니다', 'isUser': false});
        _isWaiting = false; // 생각 끝! 스위치 끄기
      });
    }
  }

  // 🛑 중지 버튼 누르기
  void _stopGenerating() {
    setState(() {
      _isWaiting = false; // 생각 중 스위치를 강제로 꺼버림

      // 방금 보낸 내 말풍선 지우기
      if (_messages.isNotEmpty) {
        // 화면에서 지우는 대신, 오타를 고칠 수 있게 입력창에 다시 글씨를 띄움
        _messageController.text = _messages.last['text'];

        // 내 말풍선을 화면(리스트)에서 삭제
        _messages.removeLast();
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.white,

      // 1. 햄버거 메뉴 (왼쪽 서랍)
      drawer: Drawer(
        backgroundColor: const Color(0xFFFCFCFC),
        child: SafeArea(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const SizedBox(height: 20),
              ListTile(
                leading: const Icon(Icons.chat_bubble_outline),
                title: const Text(
                  '새 채팅',
                  style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
                ),
                onTap: () {
                  Navigator.pop(context);
                  setState(() {
                    _messages.clear(); // 채팅 내역 지우고 새 채팅 시작
                  });
                },
              ),
              ListTile(
                leading: const Icon(Icons.library_books_outlined),
                title: const Text(
                  '라이브러리',
                  style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
                ),
                onTap: () {
                  Navigator.pop(context);
                  Navigator.push(
                    context,
                    MaterialPageRoute(
                      builder: (context) => const ChatLibraryScreen(),
                    ),
                  );
                },
              ),
            ],
          ),
        ),
      ),

      // 2. 메인 채팅 화면
      body: SafeArea(
        child: Column(
          children: [
            // 상단 햄버거 메뉴 버튼
            Padding(
              padding: const EdgeInsets.only(left: 16.0, top: 10.0),
              child: Align(
                alignment: Alignment.centerLeft,
                child: Builder(
                  builder: (context) {
                    return IconButton(
                      icon: const Icon(
                        Icons.menu,
                        size: 30,
                        color: Colors.black,
                      ),
                      onPressed: () => Scaffold.of(context).openDrawer(),
                    );
                  },
                ),
              ),
            ),

            // 채팅 내용이 보여질 공간
            Expanded(
              child: _messages.isEmpty
                  ? const Center(
                      child: Text(
                        '무엇이든 물어보세요',
                        style: TextStyle(
                          fontSize: 22,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                    )
                  // 💡 로딩 중일 때는 리스트 끝에 '로딩 말풍선' 하나를 더 추가
                  : ListView.builder(
                      padding: const EdgeInsets.all(20),
                      itemCount: _messages.length + (_isWaiting ? 1 : 0),
                      itemBuilder: (context, index) {
                        // 1. AI 생각 중 (로딩) 말풍선
                        if (index == _messages.length && _isWaiting) {
                          return Align(
                            alignment: Alignment.centerLeft,
                            child: Container(
                              margin: const EdgeInsets.only(bottom: 12),
                              padding: const EdgeInsets.all(16),
                              decoration: BoxDecoration(
                                color: Colors.white, // AI 말풍선 배경 (흰색)
                                border: Border.all(
                                  color: Colors.black,
                                  width: 1.5,
                                ), // 검정 테두리
                                borderRadius: BorderRadius.circular(15),
                              ),
                              child: const SizedBox(
                                width: 20,
                                height: 20,
                                child: CircularProgressIndicator(
                                  strokeWidth: 2,
                                  color: Colors.black,
                                ),
                              ),
                            ),
                          );
                        }

                        // 2. 진짜 채팅 말풍선들
                        bool isUser = _messages[index]['isUser'];
                        return Align(
                          alignment: isUser
                              ? Alignment.centerRight
                              : Alignment.centerLeft,
                          child: Container(
                            margin: const EdgeInsets.only(bottom: 12),
                            // 그림 테두리 두께에 맞춰서 글씨가 밖으로 안 튀어나오게 안쪽 여백(padding)을 조절해 줘!
                            padding: const EdgeInsets.symmetric(
                              horizontal: 20,
                              vertical: 16,
                            ),

                            // user_chat / ai_chat 이미지를 불러옴
                            // 다시 이미지를 불러오되, centerSlice를 뺌
                            decoration: isUser
                                ? const BoxDecoration(
                                    image: DecorationImage(
                                      image: AssetImage('assets/user_chat.png'),
                                      // 🛑 여기가 범인이었어! 일단 이 줄을 꺼봐!
                                      // centerSlice: Rect.fromLTRB(20, 20, 80, 80),
                                      fit: BoxFit.fill,
                                    ),
                                  )
                                : const BoxDecoration(
                                    image: DecorationImage(
                                      image: AssetImage('assets/ai_chat.png'),
                                      // 🛑 여기도 똑같이 꺼주기!
                                      // centerSlice: Rect.fromLTRB(20, 20, 80, 80),
                                      fit: BoxFit.fill,
                                    ),
                                  ),
                            child: Text(
                              _messages[index]['text'],
                              style: const TextStyle(
                                fontSize: 15,
                                // 글씨 색상은 그림 배경색에 맞춰서 마음대로 바꿔도 됨
                                color: Colors.black,
                              ),
                            ),
                          ),
                        );
                      },
                    ),
            ),

            // 하단 채팅 입력창
            Padding(
              padding: const EdgeInsets.symmetric(
                horizontal: 20.0,
                vertical: 16.0,
              ),
              child: Row(
                children: [
                  Expanded(
                    child: Container(
                      height: 48,
                      decoration: BoxDecoration(
                        color: Colors.white,
                        border: Border.all(color: Colors.black, width: 2),
                        borderRadius: BorderRadius.circular(15),
                      ),
                      child: TextField(
                        controller: _messageController,
                        decoration: const InputDecoration(
                          hintText: '입력해주세요',
                          hintStyle: TextStyle(color: Color(0xFF6B6868)),
                          border: InputBorder.none,
                          contentPadding: EdgeInsets.symmetric(
                            horizontal: 16,
                            vertical: 12,
                          ),
                        ),
                        // 💡 로딩 중일 때는 엔터 쳐도 안 쳐지게 막기
                        onSubmitted: _isWaiting
                            ? null
                            : (value) => _sendMessage(),
                      ),
                    ),
                  ),
                  const SizedBox(width: 10),

                  // 전송 / 중지 버튼!
                  GestureDetector(
                    // 💡 기다리는 중이면 '중지 함수'를, 아니면 '전송 함수'를 실행!
                    onTap: _isWaiting ? _stopGenerating : _sendMessage,
                    child: Container(
                      width: 45,
                      height: 45,
                      decoration: const BoxDecoration(
                        color: Colors.black,
                        shape: BoxShape.circle,
                      ),
                      // 💡 기다리는 중이면 네모(중지) 아이콘, 아니면 화살표(전송) 아이콘
                      child: Icon(
                        _isWaiting ? Icons.stop_rounded : Icons.arrow_upward,
                        color: Colors.white,
                        size: 28,
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

// -------------------------------------------------------------
// 📚 라이브러리(채팅 내역) 화면
// -------------------------------------------------------------
class ChatLibraryScreen extends StatefulWidget {
  const ChatLibraryScreen({super.key});

  @override
  State<ChatLibraryScreen> createState() => _ChatLibraryScreenState();
}

class _ChatLibraryScreenState extends State<ChatLibraryScreen> {
  // 가짜 채팅 내역 데이터
  List<Map<String, String>> chatHistory = [
    {'date': '7월 14일', 'title': '배송 지연으로 인한 제품 환불 가능 여부'},
    {'date': '7월 10일', 'title': '단순 변심으로 구매 취소했을 때 반환 비용'},
  ];

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.white,
      appBar: AppBar(
        backgroundColor: Colors.white,
        elevation: 0,
        // 뒤로가기 버튼
        leading: IconButton(
          icon: const Icon(Icons.arrow_back_ios, color: Colors.black),
          onPressed: () => Navigator.pop(context),
        ),
      ),
      body: ListView.builder(
        padding: const EdgeInsets.all(24.0),
        itemCount: chatHistory.length,
        itemBuilder: (context, index) {
          return Padding(
            padding: const EdgeInsets.only(bottom: 24.0),
            // 💡 Row를 써서 글씨는 왼쪽, 휴지통은 오른쪽에 배치!
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                // 1. 날짜와 제목 (남은 공간을 다 차지하게 Expanded 사용)
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        chatHistory[index]['date']!,
                        style: const TextStyle(
                          color: Color(0xFFA3A0A0),
                          fontSize: 14,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                      const SizedBox(height: 8),
                      Text(
                        chatHistory[index]['title']!,
                        style: const TextStyle(
                          color: Colors.black,
                          fontSize: 16,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                    ],
                  ),
                ),

                // 2. 💡 개별 삭제(휴지통) 버튼!
                IconButton(
                  icon: const Icon(Icons.delete_outline, color: Colors.black54),
                  onPressed: () {
                    // 휴지통을 누르면 그 줄(index)의 채팅 내역만 지워짐
                    setState(() {
                      chatHistory.removeAt(index);
                    });
                  },
                ),
              ],
            ),
          );
        },
      ),
    );
  }
}
