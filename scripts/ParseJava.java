import java.nio.file.*;
import java.util.*;
import javax.tools.*;
import com.sun.source.util.JavacTask;

// Syntax parsing only. This does not resolve Spring symbols, compile the app, or run tests.
public final class ParseJava {
 public static void main(String[] args) throws Exception {
  JavaCompiler compiler=ToolProvider.getSystemJavaCompiler();
  DiagnosticCollector<JavaFileObject> diagnostics=new DiagnosticCollector<>();
  try(var manager=compiler.getStandardFileManager(diagnostics,null,null);var files=Files.walk(Path.of(args[0]))) {
   var sources=files.filter(p->p.toString().endsWith(".java")).map(Path::toFile).toList();
   var task=(JavacTask)compiler.getTask(null,manager,diagnostics,List.of("--release","17","-proc:none"),null,manager.getJavaFileObjectsFromFiles(sources));
   task.parse();
   var errors=diagnostics.getDiagnostics().stream().filter(d->d.getKind()==Diagnostic.Kind.ERROR).toList();
   for(var error:errors)System.err.println(error);
   if(!errors.isEmpty())System.exit(1);
   System.out.println("Parsed "+sources.size()+" Java files without syntax errors. Dependency resolution was not performed.");
  }
 }
}
