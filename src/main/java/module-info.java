// FILE: P1/src/main/java/module-info.java

module networkoperatorsimulator.appmodule {
    // وابستگی به ماژول‌های استاندارد جاوا
    requires java.desktop;

    // ===== بخش اضافه شده برای لاگ‌گیری =====
    // اعلام نیاز به ماژول SLF4J API
    requires org.slf4j;
    // اعلام نیاز به ماژول Logback Classic (که خودکار Logback Core را هم می‌آورد)
    requires ch.qos.logback.classic;
    // =======================================

    // اکسپورت کردن پکیج اصلی برنامه
    exports com.networkopsim.game;
}