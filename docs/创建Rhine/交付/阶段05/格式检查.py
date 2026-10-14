from pathlib import Path
import subprocess,sys,shutil
root=Path(__file__).resolve().parents[4]
cache=Path('/home/lumine/.gradle/caches/modules-2/files-2.1')
patterns=['com.facebook/ktfmt/0.61/**/*.jar','org.jetbrains.kotlin/kotlin-compiler-embeddable/2.2.0/**/*.jar','org.jetbrains.kotlin/kotlin-stdlib/2.2.0/**/*.jar','com.google.guava/guava/33.3.1-jre/**/*.jar','com.google.googlejavaformat/google-java-format/1.23.0/**/*.jar','org.jetbrains.intellij.deps/trove4j/**/*.jar','org.jetbrains/annotations/**/*.jar','org.jetbrains.kotlinx/kotlinx-coroutines-core-jvm/1.10.2/**/*.jar']
cp=':'.join(str(p) for pat in patterns for p in cache.glob(pat))
tracked=subprocess.check_output(['git','diff','--name-only','HEAD'],cwd=root,text=True).splitlines()
new=subprocess.check_output(['git','ls-files','--others','--exclude-standard'],cwd=root,text=True).splitlines()
files=[str(root/f) for f in tracked+new if f.endswith('.kt')]
Path(__file__).with_name('本期Kotlin文件.txt').write_text('\n'.join(files)+'\n')
java='/home/lumine/jdk-21/bin/java' if Path('/home/lumine/jdk-21/bin/java').exists() else shutil.which('java')
cmd=[java,'-cp',cp,'com.facebook.ktfmt.cli.Main','--kotlinlang-style']
if '--check' in sys.argv:cmd+=['--dry-run','--set-exit-if-changed']
sys.exit(subprocess.call(cmd+files))
