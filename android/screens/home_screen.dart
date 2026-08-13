import 'package:flutter/material.dart';

import 'chat_screen.dart'; // 챗봇 화면을 사용하기 위해 추가
import 'analytics_screen.dart'; // 분석 화면을 사용하기 위해 추가
import 'settings_screen.dart'; // 설정 화면을 사용하기 위해 추가

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  // 현재 선택된 하단 탭의 순서를 기억하는 변수 (0: 콜핑, 1: 챗봇, 2: 분석, 3: 설정)
  int _selectedIndex = 0;

  @override
  Widget build(BuildContext context) {
    // 📺 1. TV 채널 4개 준비하기
    final List<Widget> pages = [
      _buildMainTab(), // 0번 채널: 새로 바뀐 콜핑 메인 화면!
      const ChatScreen(), // 1번 채널: 챗봇 화면
      const Center(
        child: Text('분석 화면 준비 중 📊', style: TextStyle(fontSize: 24)),
      ),
      const Center(
        child: Text('설정 화면 준비 중 ⚙️', style: TextStyle(fontSize: 24)),
      ),
    ];

    return Scaffold(
      backgroundColor: Colors.white,

      // 2. 하단 네비게이션 바 (네가 만든 이미지 아이콘 그대로!)
      bottomNavigationBar: BottomNavigationBar(
        currentIndex: _selectedIndex,
        onTap: (index) {
          setState(() {
            _selectedIndex = index;
          });
        },
        type: BottomNavigationBarType.fixed,
        selectedItemColor: const Color(0xFF1A9000),
        unselectedItemColor: Colors.black,
        items: [
          BottomNavigationBarItem(
            icon: Image.asset(
              'assets/home.png',
              width: 24,
              height: 24,
              color: _selectedIndex == 0
                  ? const Color(0xFF1A9000)
                  : Colors.black,
            ),
            label: '콜핑',
          ),
          BottomNavigationBarItem(
            icon: Image.asset(
              'assets/chat.png',
              width: 24,
              height: 24,
              color: _selectedIndex == 1
                  ? const Color(0xFF1A9000)
                  : Colors.black,
            ),
            label: '챗봇',
          ),
          BottomNavigationBarItem(
            icon: Image.asset(
              'assets/analytic.png',
              width: 24,
              height: 24,
              color: _selectedIndex == 2
                  ? const Color(0xFF1A9000)
                  : Colors.black,
            ),
            label: '분석',
          ),
          BottomNavigationBarItem(
            icon: Image.asset(
              'assets/setting.png',
              width: 24,
              height: 24,
              color: _selectedIndex == 3
                  ? const Color(0xFF1A9000)
                  : Colors.black,
            ),
            label: '설정',
          ),
        ],
      ),

      // 3. 화면 가운데에 선택된 채널 틀어주기
      body: pages[_selectedIndex],
    );
  }

  // -------------------------------------------------------------
  // 🛠️ 0번 채널: 새로 디자인한 메인 화면 ("체크리스트를 만들어보세요")
  // -------------------------------------------------------------
  Widget _buildMainTab() {
    return SafeArea(
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 24.0, vertical: 20.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            const SizedBox(height: 20), // 위쪽에 숨 쉴 틈 살짝 주기
            // 1. 상단 체크리스트 박스
            Container(
              height: 254,
              padding: const EdgeInsets.all(20),
              decoration: BoxDecoration(
                color: Colors.white,
                border: Border.all(color: Colors.black, width: 2),
                borderRadius: BorderRadius.circular(10),
              ),
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  const Icon(
                    Icons.playlist_add_circle_outlined,
                    size: 80,
                    color: Color(0xFFD9D9D9),
                  ),
                  const SizedBox(height: 20),
                  const Text(
                    '체크리스트를 만들어보세요',
                    textAlign: TextAlign.center,
                    style: TextStyle(
                      color: Color(0xFF6A6767),
                      fontSize: 14,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                ],
              ),
            ),

            // 🚀 마법의 투명 스프링 1 (상단 박스와 중간 버튼 사이를 쫙 벌려줌!)
            const Spacer(flex: 2),

            // 2. 통화 가이드 & 대본 보관함 버튼
            Row(
              children: [
                Expanded(
                  child: _buildMenuButton('통화 가이드', () {
                    print('통화 가이드 클릭됨!');
                  }),
                ),
                const SizedBox(width: 16),
                Expanded(
                  child: _buildMenuButton('대본 보관함', () {
                    print('대본 보관함 클릭됨!');
                  }),
                ),
              ],
            ),

            const SizedBox(height: 24), // 피그마처럼 여기는 간격을 조금만 띄우기
            // 3. 파일 업로드 버튼
            _buildMenuButton('파일 업로드', () {
              print('파일 업로드 클릭됨!');
            }),

            // 🚀 마법의 투명 스프링 2 (버튼 밑에서 하단 탭까지 남는 공간을 밀어줌!)
            const Spacer(flex: 3),
          ],
        ),
      ),
    );
  }

  // -------------------------------------------------------------
  // 🛠️ 메뉴 버튼을 만들어주는 나만의 맞춤 부품 (위젯)
  // -------------------------------------------------------------
  Widget _buildMenuButton(String title, VoidCallback onTap) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(15),
      child: Container(
        height: 74,
        alignment: Alignment.center,
        decoration: BoxDecoration(
          color: Colors.white,
          border: Border.all(color: Colors.black, width: 2),
          borderRadius: BorderRadius.circular(15),
        ),
        child: Text(
          title,
          style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
        ),
      ),
    );
  }
}
