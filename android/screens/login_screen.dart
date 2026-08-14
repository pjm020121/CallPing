// import 'package:callping/home_screen.dart';
import 'package:flutter/material.dart';
import 'home_screen.dart';

// 💡 나중에 진짜 홈 화면(home_screen.dart) 파일을 만들면
// 맨 윗줄에 그 파일을 import 해주고, 맨 밑에 있는 가짜 HomeScreen 클래스는 지워주면 돼!

class LoginScreen extends StatefulWidget {
  const LoginScreen({super.key});

  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  final TextEditingController _idController = TextEditingController();
  final TextEditingController _passwordController = TextEditingController();

  @override
  void dispose() {
    _idController.dispose();
    _passwordController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.white,
      body: SafeArea(
        child: Center(
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 47.0),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                // 1. 콜핑 로고 타이틀
                const Text(
                  'CallPing',
                  style: TextStyle(
                    color: Colors.black,
                    fontSize: 50,
                    fontFamily: 'Ysabeau SC',
                  ),
                ),
                const SizedBox(height: 60),

                // 2. 아이디 입력창
                TextField(
                  controller: _idController,
                  decoration: InputDecoration(
                    hintText: '아이디',
                    hintStyle: const TextStyle(color: Colors.black54),
                    filled: true,
                    fillColor: const Color(0xFFD9D9D9).withOpacity(0.4),
                    border: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(10),
                      borderSide: BorderSide.none,
                    ),
                  ),
                ),
                const SizedBox(height: 20),

                // 3. 비밀번호 입력창
                TextField(
                  controller: _passwordController,
                  obscureText: true,
                  decoration: InputDecoration(
                    hintText: '비밀번호',
                    hintStyle: const TextStyle(color: Colors.black54),
                    filled: true,
                    fillColor: const Color(0xFFD9D9D9).withOpacity(0.4),
                    border: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(10),
                      borderSide: BorderSide.none,
                    ),
                  ),
                ),
                const SizedBox(height: 40),

                // 4. 로그인 버튼 (여기에 화면 이동 코드가 들어갔어!)
                // 4. 완벽한 안전장치가 추가된 로그인 버튼
                SizedBox(
                  width: double.infinity,
                  height: 55,
                  child: ElevatedButton(
                    style: ElevatedButton.styleFrom(
                      backgroundColor: Colors.black,
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(10),
                      ),
                    ),
                    onPressed: () {
                      String inputId = _idController.text;
                      String inputPassword = _passwordController.text;

                      // 🛑 1차 방어: 빈칸이 하나라도 있으면 경고창(SnackBar) 띄우기
                      if (inputId.isEmpty || inputPassword.isEmpty) {
                        ScaffoldMessenger.of(context).showSnackBar(
                          const SnackBar(
                            content: Text('아이디와 비밀번호를 입력해주세요!'),
                            backgroundColor: Colors.redAccent, // 빨간색 알림
                            duration: Duration(seconds: 2), // 2초간 띄움
                          ),
                        );
                        return; // ⬅️ 여기서 코드를 멈추고 밑으로 안 내려감!
                      }

                      // 🛑 2차 방어: 가짜 DB 검사 (test / 1234가 아니면 막기)
                      if (inputId != 'test' || inputPassword != '1234') {
                        // 팝업창(Dialog) 띄우기
                        showDialog(
                          context: context,
                          builder: (context) {
                            return AlertDialog(
                              title: const Text('로그인 실패'),
                              content: const Text(
                                '가입되지 않은 아이디이거나 비밀번호가 틀렸습니다. 회원가입을 먼저 진행해주세요.',
                              ),
                              actions: [
                                TextButton(
                                  onPressed: () {
                                    Navigator.pop(context); // 팝업창 닫기
                                  },
                                  child: const Text(
                                    '확인',
                                    style: TextStyle(color: Colors.black),
                                  ),
                                ),
                              ],
                            );
                          },
                        );
                        return; // ⬅️ 마찬가지로 틀렸으니 여기서 멈춤!
                      }

                      // 🟢 모든 검사를 통과했을 때만 (test / 1234 입력 시) 홈 화면으로 이동!
                      Navigator.pushReplacement(
                        context,
                        MaterialPageRoute(
                          builder: (context) => const HomeScreen(),
                        ),
                      );
                    },
                    child: const Text(
                      'Log in',
                      style: TextStyle(color: Colors.white, fontSize: 20),
                    ),
                  ),
                ),
                const SizedBox(height: 30),

                // 5. 하단 텍스트 링크들
                Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    GestureDetector(
                      onTap: () {
                        print('아이디 찾기 클릭됨!');
                      },
                      child: const Text(
                        '아이디 찾기',
                        style: TextStyle(
                          color: Color(0xFFABABAB),
                          fontSize: 12,
                        ),
                      ),
                    ),
                    const Text(
                      '  /  ',
                      style: TextStyle(color: Color(0xFFABABAB), fontSize: 12),
                    ),
                    GestureDetector(
                      onTap: () {
                        print('비밀번호 찾기 클릭됨!');
                      },
                      child: const Text(
                        '비밀번호 찾기',
                        style: TextStyle(
                          color: Color(0xFFABABAB),
                          fontSize: 12,
                        ),
                      ),
                    ),
                    const Text(
                      '  /  ',
                      style: TextStyle(color: Color(0xFFABABAB), fontSize: 12),
                    ),
                    GestureDetector(
                      onTap: () {
                        print('회원가입 클릭됨!');
                      },
                      child: const Text(
                        '회원가입',
                        style: TextStyle(
                          color: Color(0xFFABABAB),
                          fontSize: 12,
                        ),
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
