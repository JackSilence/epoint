目的: 每日定時排程發送某電子商城之個人帳號所擁有的點數資訊

機制: 將此應用程式部署在Heroku上, 透過UptimeRobot定時監控避免使用Heroku免費帳號的休眠問題

Point - 透過Selenium + Headless Chrome自動登入帳號後獲取點數頁面資訊, 用SendGrid寄出通知信.

登入驗證碼使用Tess4j + Tesseract進行OCR解析後得到, 驗證碼錯誤時會自動重試

ExecuteController - 手動執行Task使用. 將Spring ApplicationContext中符合名稱的Bean取出呼叫execute方法執行