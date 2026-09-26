# Security Lock (Prank App)

Android Studio mein open karo (File → Open → is folder ko select karo), Gradle sync hone do, phir Run karo ya GitHub pe push kar do.

- PIN change karne ke liye: `app/src/main/java/com/prank/lockapp/MainActivity.kt` mein `correctPin` variable dhoondo.
- App icon: laal background pe safed lock (`res/mipmap-anydpi-v26/ic_launcher.xml`).
- Pehli baar khulte hi Device Admin permission mangega — Allow karna zaroori hai warna uninstall-block wala feature kaam nahi karega.
- Sirf apne phone ya razamandi wale dost ke phone pe use karo, mazaak ke tor par.
