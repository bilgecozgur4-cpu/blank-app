from pathlib import Path
import sys
root=Path(sys.argv[1] if len(sys.argv)>1 else '.')
p=root/'android-native/app/src/main/java/com/ozbiltar/yonetim/MainActivity.java'
s=p.read_text(encoding='utf-8')
s=s.replace('import com.journeyapps.barcodescanner.IntentIntegrator;','import com.google.zxing.integration.android.IntentIntegrator;')
s=s.replace('import com.journeyapps.barcodescanner.IntentResult;','import com.google.zxing.integration.android.IntentResult;')
p.write_text(s,encoding='utf-8')
print('QR imports fixed')
