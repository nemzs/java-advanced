package ru.ifmo.ctddev.osipov.implementor;

import info.kgeorgiy.java.advanced.implementor.ImplerException;
import info.kgeorgiy.java.advanced.implementor.JarImpler;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.*;
import java.lang.annotation.Annotation;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;

/**
 * Created by Nemzs on 27.02.2017.
 */

/**
 * Provides implementation for interface and {@link JarImpler}.
 */
public class Implementor implements JarImpler {
    /**
     * Simple name of class to implement.
     */
    private String interfaceName = "";
    /**
     * Default indent in code.
     */
    private String TAB = "\t";
    /**
     * Type token to implement.
     */
    private Class<?> cls;

    /**
     * Internal string buffer, used to write constructors or methods by appending each elements during the generation.
     * <p>
     * Resets after writing each method to file.
     * </p>
     *
     * @see StringBuilder
     */
    private StringBuilder sb = new StringBuilder();

    /**
     * Produces code implementing for java class or interface specified by provided <tt>token</tt>.
     * <p>
     * Generated class full name have to contains of type token full name and suffix <tt>Impl</tt>
     * Generated code should be placed in the correct subdirectory of the specified <tt>root</tt>
     * directory and have correct filename i.e. the implementation of the
     * interface {@link java.util.List} should be placed into <tt>$root/java/util/ListImpl.java</tt>
     *
     * @param token type token to create implementation for
     * @param root  root directory
     * @throws ImplerException {@link info.kgeorgiy.java.advanced.implementor.ImplerException}
     *                         when implementation can't be generated
     */
    @Override
    public void implement(Class<?> token, Path root) throws ImplerException {

        if (root == null || token == null) {
            throw new ImplerException("Wrong arguments, here is null, something is wrong here... oops");
        }
        if (token.isPrimitive() || token.isArray()) {
            throw new ImplerException("token should be an interface");
        }
        cls = token;
        interfaceName = cls.getSimpleName() + "Impl";

        try (PrintWriter printWriter = new PrintWriter(
                Files.newBufferedWriter(getFileName(cls, root)))) {
            printPackage(printWriter, cls);
            printHeader(printWriter);
            printMethods(printWriter);
            printWriter.println();
        } catch (IOException e) {
            throw new ImplerException(e);
        }
    }

    /**
     * Receives and prints package of implementing class by using {@link Writer} into the required file.
     *
     * @param printWriter output destination.
     * @param cls class token
     * @throws IOException error during writing into required file.
     */
    private void printPackage(Writer printWriter, Class cls) throws IOException {
        if (cls.getPackage() != null) {
            printWriter.write("package " + cls.getPackage().getName() + ";\n");
        }
    }

    /**
     * Receives and prints header of implementing class by using {@link Writer} into the required file.
     *
     * @param printWriter output destination.
     * @throws IOException error during writing into required file.
     */
    private void printHeader(Writer printWriter) throws IOException {
        sb.setLength(0);
        printWriter.write(sb.append("public class ")
                .append(interfaceName)
                .append(" implements ")
                .append(cls.getSimpleName())
                .append(" {\n").toString());
    }

    private static void printAnnotations(Writer printWriter, Annotation[] annotations) throws IOException {
        for (Annotation curAnn : annotations) {
            printWriter.write(curAnn.toString() + "\n");
        }
    }

    /**
     * Receives and prints methods of implementing class by using {@link Writer} into the required file.
     *
     * @param printWriter output destination.
     * @throws IOException error during writing into required file.
     */
    private void printMethods(PrintWriter printWriter) throws IOException {
        for (Method method : cls.getMethods()) {
            printAnnotations(printWriter, method.getAnnotations());
            printMethod(method, printWriter);
        }
        printWriter.println("}");
    }

    /**
     * Receives and prints current method of implementing class by using {@link Writer} into the required file.
     *
     * @param method      current method of implementing file
     *                    which was received by using {@link Implementor#printMethods(PrintWriter)}.
     * @param printWriter output destination.
     * @throws IOException error during printing method.
     */
    private void printMethod(Method method, PrintWriter printWriter) throws IOException {
        printHeader(printWriter, method.getName(), method);
        StringBuilder sb = new StringBuilder(" {\n");

        if (method.getReturnType() != void.class) {
            sb.append(TAB)
                    .append(TAB)
                    .append("return ")
                    .append("\n")
                    .append(getReturnedValueByDefaultMethod(method.getReturnType()))
                    .append(";");
        }
        printWriter.println(sb.append(TAB)
                .append("}")
                .toString());
    }

    /**
     * Prints header of {@link Executable} by using {@link Writer}.
     * <p>
     * Prints return type of executable if it is a method, name of executable,
     * parameters it takes (with fully qualified type names) and exceptions it can throw.
     * </p>
     *
     * @param printWriter    output destination.
     * @param executableName name of executable to be printed.
     * @param executable     executable whose header is printed.
     * @throws IOException error during printing header.
     */
    private void printHeader(Writer printWriter, String executableName, Executable executable)
            throws IOException {
        sb.setLength(0);
        sb.append("\n").
                append(TAB)
                .append("public ")
                .append(((Method) executable).getReturnType().getCanonicalName())
                .append(" ")
                .append(executableName)
                .append("(");
        printWriter.write(sb.toString());
        printArguments(printWriter, executable);
        printExceptions(printWriter, executable);
    }

    /**
     * Prints arguments of {@link Executable} by using {@link Writer}.
     *
     * @param printWriter output destination.
     * @param executable  executable whose header is printed.
     * @throws IOException error during printing arguments.
     */
    private void printArguments(Writer printWriter, Executable executable) throws IOException {
        Parameter[] parameters = executable.getParameters();
        printWriter.write(Arrays.stream(parameters)
                .map((param) ->
                        param.getType().getCanonicalName()
                                + " "
                                + param.getName())
                .collect(Collectors.joining(", ")));
        printWriter.write(")");
    }

    /**
     * Prints exceptions of {@link Executable} by using {@link Writer}.
     *
     * @param printWriter output destination.
     * @param executable  executable whose header is printed.
     * @throws IOException error during printing arguments.
     */
    private void printExceptions(Writer printWriter, Executable executable) throws IOException {
        Class<?> exceptions[] = executable.getExceptionTypes();
        if (exceptions.length > 0) {
            printWriter.write(" throws");
            for (int i = 0; i < exceptions.length; i++) {
                if (i != 0) {
                    printWriter.write(",");
                }
                printWriter.write(" " + exceptions[i].getCanonicalName());
            }
        }
    }

    /**
     * Generates the string representing the default value of class with given <code>token</code>.
     * <p>
     * It is <tt>null</tt> for non-primitive types,
     * <tt>false</tt> for {@link Boolean boolean},
     * empty string for {@link Void void}
     * and <tt>0</tt> for other.
     *
     * @param type type token to get default value for.
     * @return {@link java.lang.String} with default value.
     */
    private String getReturnedValueByDefaultMethod(Class<?> type) {
        if (type.equals(boolean.class)) {
            return "true";
        } else if (type.equals(void.class)) {
            return "";
        } else {
            return type.isPrimitive() ? "0" : "null";
        }
    }

    /**
     * Builds a path, where implementation of <code>clazz</code> should be placed.
     * Implementation file has suffix <tt>Impl.java</tt> and
     * is placed in <tt>$root/&lt;package-dirs&gt;/</tt> folder.
     * <p>
     * All directories are creating automatically.
     *
     * @param cls  Token to resolve implementation path for.
     * @param path Directory to be treated as packages root.
     * @return Path to file with implementation.
     * @throws IOException If creation of directories has failed.
     */
    private static Path getFileName(Class<?> cls, Path path) throws IOException {
        if (cls.getPackage() != null) {
            path = path.resolve(cls.getPackage()
                    .getName()
                    .replace('.', '/')
                    + "/");
            Files.createDirectories(path);
        }
        return path.resolve(cls.getSimpleName() + "Impl.java");
    }

    /**
     * Entry point of the program for command line arguments.
     * <p>
     * Usage:
     * <ul>
     * <li>{@code java -jar Implementor.jar -jar class-to-implement path-to-jar}</li>
     * <li>{@code java -jar Implementor.jar class-to-implement path-to-class}</li>
     * </ul>
     *
     * @param args command line arguments.
     */
    public static void main(String[] args) {
        Implementor implementor = new Implementor();
        try {
            if (args[0].equals("-jar")) {
                implementor.implementJar(Class.forName(args[1]), Paths.get(args[2]));
            } else {
                implementor.implement(Class.forName(args[0]), Paths.get(args[1]));
            }
        } catch (IndexOutOfBoundsException e) {
            System.err.println("not enough args");
        } catch (ClassNotFoundException e) {
            System.err.println("no such class " + e.getMessage());
        } catch (ImplerException e) {
            System.err.println("error while impl class " + e.getMessage());
        } catch (InvalidPathException e) {
            System.err.println("invalid path " + e.getMessage());
        }
    }

    /**
     * Creates <tt>.jar</tt> file of implemented class or interface <code>aClass</code>
     * by using {@link Implementor#implement(Class, Path)} new file have to be printed into the correct
     * subdirectory according to <code>path</code>
     * <p>
     * Generated class full name should be same as full name of the type token with <tt>Impl</tt>
     * suffix added.
     * </p>
     *
     * @param aClass type token to create implementation for.
     * @param path   target <tt>.jar</tt> file.
     * @throws ImplerException {@link ImplerException} when implementation cannot be generated.
     */
    @Override
    public void implementJar(Class<?> aClass, Path path) throws ImplerException {
        try {
            Path tmp = Paths.get(".");
            this.implement(aClass, tmp);
            Path fileToCompile = getFileName(aClass, tmp).normalize();
            compileClass(tmp, fileToCompile);
            Path classfile = getClassPathJar(fileToCompile);
            printJar(path, classfile);
            classfile.toFile().deleteOnExit();
        } catch (IOException e) {
            throw new ImplerException("IO exception " + e.getMessage());
        }
    }

    /**
     * Generates a path to compiled .class file by path to .java file by replacing
     * <code>execFileName</code> .java extension to .class extension.
     *
     * @param execFileName A path to .java file.
     * @return A path to corresponding .class file in same folder.
     * @throws IllegalArgumentException If <code>execFileName</code> doesn't have .java extension.
     */
    public static Path getClassPathJar(Path execFileName) {
        String pathStr = execFileName.toString();
        if (pathStr.endsWith(".java")) {
            return Paths.get(pathStr.substring(0, pathStr.length() - 5) + ".class");
        } else {
            throw new IllegalArgumentException("It's not a java file");
        }
    }

    /**
     * Creates jar archive at <code>jarPath</code> with given <code>jarPath</code>
     * and copies existing <code>fileToPrint</code> to it.
     * <p>
     * If archive already exists, overwrites it.
     * <p>
     * Creates <tt>MANIFEST.MF</tt> file too with the <tt>MAIN_CLASS</tt> named <code>fileToPrint</code>
     * and  <tt>MANIFEST_VERSION</tt>: <tt>1.0</tt>
     * </p>
     *
     * @param jarPath     The path for jar archive. Directories on path should exist.
     * @param fileToPrint The name of file to be copied into archive. Should exist.
     * @throws IOException If I/O error occurred, e.g. if <code>fileName</code> of <code>jarDirectory</code>
     *                     don't exist, or if unexpected error happened during creating or writing to archive.
     */
    public static void printJar(Path jarPath, Path fileToPrint) throws IOException {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0\n");
        manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, fileToPrint.toString());
        try (InputStream inputStream = Files.newInputStream(fileToPrint);
             JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(jarPath))) {
            int c;
            jarOutputStream.putNextEntry(new ZipEntry(fileToPrint.toString()));
            byte buffer[] = new byte[2028];
            while ((c = inputStream.read(buffer, 0, 1024)) >= 0) {
                jarOutputStream.write(buffer, 0, c);
            }
            jarOutputStream.closeEntry();

        }

    }

    /**
     * Compiles given file.
     * <p>
     * Using default java compiler, provided by {@link ToolProvider#getSystemJavaCompiler()}, compiles
     * the file located at <code>codeFileName</code>, using <code>packageRoot</code> as a classpath.
     *
     * @param packageRoot  Path to a package root, will be used as a classpath.
     * @param codeFileName Path to a file. Should already contain path to the package.
     * @throws ImplerException {@link ImplerException} when
     *                         default compiler is unavailable or compilation error has occurred
     *                         (compiler returned non-zero exit code).
     */
    public static void compileClass(Path packageRoot, Path codeFileName) throws ImplerException {
        JavaCompiler javaCompiler = ToolProvider.getSystemJavaCompiler();
        if (javaCompiler == null) {
            throw new ImplerException("Compile exception - no compiler");
        }
        int returnCode = javaCompiler.run(null, null, null, codeFileName.toString(), "-cp",
                packageRoot + File.pathSeparator + System.getProperty("java.class.path"));
        if (returnCode != 0) {
            throw new ImplerException("compiler throw non-zero exit code");
        }
    }
}