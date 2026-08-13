import 'package:flutter/material.dart';
// 로그인 화면 파일을 불러오는 코드
import 'login_screen.dart';

void main() {
  // runApp: "플러터야, 내 앱을 실행해줘!"
  runApp(const MyApp());
}

// 현관문 역할 및 앱 전체의 기본 설정
class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      debugShowCheckedModeBanner: false, // 화면 오른쪽 위 거슬리는 'DEBUG' 빨간 띠 없애기
      title: 'CallPing', // 앱 이름
      theme: ThemeData(
        scaffoldBackgroundColor: Colors.white, // 앱 전체 기본 배경색을 하얀색으로!
      ),
      // 🚀 핵심: 앱을 켜자마자 가장 먼저 띄울 화면을 지정하는 곳!
      home: const LoginScreen(),
    );
  }
}
