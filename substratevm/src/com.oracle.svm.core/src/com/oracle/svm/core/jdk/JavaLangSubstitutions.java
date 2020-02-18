/*
 * Copyright (c) 2007, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.svm.core.jdk;

import static com.oracle.svm.core.annotate.RecomputeFieldValue.Kind.Reset;
import static com.oracle.svm.core.snippets.KnownIntrinsics.readHub;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;

import org.graalvm.compiler.core.common.SuppressFBWarnings;
import org.graalvm.compiler.serviceprovider.GraalUnsafeAccess;
import org.graalvm.compiler.word.ObjectAccess;
import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.function.CLibrary;
import org.graalvm.nativeimage.impl.InternalPlatform;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.MonitorSupport;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.KeepOriginal;
import com.oracle.svm.core.annotate.NeverInline;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.RecomputeFieldValue.CustomFieldValueComputer;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.hub.ClassForNameSupport;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.util.VMError;

import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;

@TargetClass(java.lang.Object.class)
final class Target_java_lang_Object {

    @Substitute
    @TargetElement(name = "registerNatives", onlyWith = JDK11OrEarlier.class)
    private static void registerNativesSubst() {
        /* We reimplemented all native methods, so nothing to do. */
    }

    @Substitute
    @TargetElement(name = "getClass")
    private Object getClassSubst() {
        return readHub(this);
    }

    @Substitute
    @TargetElement(name = "hashCode")
    private int hashCodeSubst() {
        return System.identityHashCode(this);
    }

    @Substitute
    @TargetElement(name = "toString")
    private String toStringSubst() {
        return getClass().getName() + "@" + Long.toHexString(Word.objectToUntrackedPointer(this).rawValue());
    }

    @Substitute
    @TargetElement(name = "wait")
    private void waitSubst(long timeoutMillis) throws InterruptedException {
        ImageSingletons.lookup(MonitorSupport.class).wait(this, timeoutMillis);
    }

    @Substitute
    @TargetElement(name = "notify")
    private void notifySubst() {
        ImageSingletons.lookup(MonitorSupport.class).notify(this, false);
    }

    @Substitute
    @TargetElement(name = "notifyAll")
    private void notifyAllSubst() {
        ImageSingletons.lookup(MonitorSupport.class).notify(this, true);
    }
}

@TargetClass(className = "java.lang.ClassLoaderHelper")
final class Target_java_lang_ClassLoaderHelper {
    @Alias
    static native File mapAlternativeName(File lib);
}

@TargetClass(java.lang.Enum.class)
final class Target_java_lang_Enum {

    @Substitute
    private static Enum<?> valueOf(Class<Enum<?>> enumType, String name) {
        /*
         * The original implementation creates and caches a HashMap to make the lookup faster. For
         * simplicity, we do a linear search for now.
         */
        Enum<?>[] enumConstants = DynamicHub.fromClass(enumType).getEnumConstantsShared();
        if (enumConstants == null) {
            throw new IllegalArgumentException(enumType.getName() + " is not an enum type");
        }
        for (Enum<?> e : enumConstants) {
            if (e.name().equals(name)) {
                return e;
            }
        }
        if (name == null) {
            throw new NullPointerException("Name is null");
        } else {
            throw new IllegalArgumentException("No enum constant " + enumType.getName() + "." + name);
        }
    }
}

@TargetClass(java.lang.String.class)
final class Target_java_lang_String {

    @Substitute
    public String intern() {
        String thisStr = SubstrateUtil.cast(this, String.class);
        return ImageSingletons.lookup(StringInternSupport.class).intern(thisStr);
    }
}

@TargetClass(java.lang.Throwable.class)
@SuppressWarnings({"unused"})
final class Target_java_lang_Throwable {

    @Alias @RecomputeFieldValue(kind = Reset)//
    private Object backtrace;

    @Alias @RecomputeFieldValue(kind = Reset)//
    StackTraceElement[] stackTrace;

    @Alias String detailMessage;

    /*
     * Suppressed exception handling is disabled for now.
     */
    @Substitute
    private void addSuppressed(Throwable exception) {
        /*
         * This method is called frequently from try-with-resource blocks. The original
         * implementation performs allocations, which are problematic when allocations are disabled.
         * For now, we just do nothing until someone needs suppressed exception handling.
         */
    }

    @Substitute
    @NeverInline("Starting a stack walk in the caller frame")
    private Object fillInStackTrace() {
        stackTrace = StackTraceUtils.getStackTrace(true, KnownIntrinsics.readCallerStackPointer());
        return this;
    }

    @Substitute
    private StackTraceElement[] getOurStackTrace() {
        if (stackTrace != null) {
            return stackTrace;
        } else {
            return new StackTraceElement[0];
        }
    }

    @Substitute
    @TargetElement(onlyWith = JDK8OrEarlier.class)
    int getStackTraceDepth() {
        if (stackTrace != null) {
            return stackTrace.length;
        }
        return 0;
    }

    @Substitute
    @TargetElement(onlyWith = JDK8OrEarlier.class)
    StackTraceElement getStackTraceElement(int index) {
        if (stackTrace == null) {
            throw new IndexOutOfBoundsException();
        }
        return stackTrace[index];
    }
}

@TargetClass(java.lang.Runtime.class)
@SuppressWarnings({"static-method"})
final class Target_java_lang_Runtime {

    @Substitute
    public void loadLibrary(String libname) {
        // Substituted because the original is caller-sensitive, which we don't support
        loadLibrary0(null, libname);
    }

    @Substitute
    public void load(String filename) {
        // Substituted because the original is caller-sensitive, which we don't support
        load0(null, filename);
    }

    @Substitute
    public void runFinalization() {
    }

    @Substitute
    @Platforms(InternalPlatform.PLATFORM_JNI.class)
    private int availableProcessors() {
        if (SubstrateOptions.MultiThreaded.getValue()) {
            return Jvm.JVM_ActiveProcessorCount();
        } else {
            return 1;
        }
    }

    // Checkstyle: stop
    @Alias
    synchronized native void loadLibrary0(Class<?> fromClass, String libname);

    @Alias
    synchronized native void load0(Class<?> fromClass, String libname);
    // Checkstyle: resume
}

@TargetClass(java.lang.System.class)
final class Target_java_lang_System {

    @Alias private static PrintStream out;
    @Alias private static PrintStream err;
    @Alias private static InputStream in;

    @Substitute
    private static void setIn(InputStream is) {
        in = is;
    }

    @Substitute
    private static void setOut(PrintStream ps) {
        out = ps;
    }

    @Substitute
    private static void setErr(PrintStream ps) {
        err = ps;
    }

    @Substitute
    private static int identityHashCode(Object obj) {
        if (obj == null) {
            return 0;
        }
        DynamicHub hub = KnownIntrinsics.readHub(obj);
        int hashCodeOffset = hub.getHashCodeOffset();
        if (hashCodeOffset == 0) {
            throw VMError.shouldNotReachHere("identityHashCode called on illegal object");
        }
        UnsignedWord hashCodeOffsetWord = WordFactory.unsigned(hashCodeOffset);
        int hashCode = ObjectAccess.readInt(obj, hashCodeOffsetWord);
        if (hashCode != 0) {
            return hashCode;
        }

        /* On the first invocation for an object create a new hash code. */
        hashCode = IdentityHashCodeSupport.generateHashCode();

        if (!GraalUnsafeAccess.getUnsafe().compareAndSwapInt(obj, hashCodeOffset, 0, hashCode)) {
            /* We lost the race, so there now must be a hash code installed from another thread. */
            hashCode = ObjectAccess.readInt(obj, hashCodeOffsetWord);
        }
        VMError.guarantee(hashCode != 0, "Missing identity hash code");
        return hashCode;
    }

    /* Ensure that we do not leak the full set of properties from the image generator. */
    @Delete //
    private static Properties props;

    @Substitute
    private static Properties getProperties() {
        return ImageSingletons.lookup(SystemPropertiesSupport.class).getProperties();
    }

    @Substitute
    private static void setProperties(Properties props) {
        ImageSingletons.lookup(SystemPropertiesSupport.class).setProperties(props);
    }

    @Substitute
    public static String setProperty(String key, String value) {
        checkKey(key);
        return ImageSingletons.lookup(SystemPropertiesSupport.class).setProperty(key, value);
    }

    @Substitute
    private static String getProperty(String key) {
        checkKey(key);
        return ImageSingletons.lookup(SystemPropertiesSupport.class).getProperty(key);
    }

    @Substitute
    public static String clearProperty(String key) {
        checkKey(key);
        return ImageSingletons.lookup(SystemPropertiesSupport.class).clearProperty(key);
    }

    @Substitute
    private static String getProperty(String key, String def) {
        String result = getProperty(key);
        return result != null ? result : def;
    }

    @Alias
    private static native void checkKey(String key);

    @Substitute
    public static void loadLibrary(String libname) {
        // Substituted because the original is caller-sensitive, which we don't support
        Runtime.getRuntime().loadLibrary(libname);
    }

    @Substitute
    public static void load(String filename) {
        // Substituted because the original is caller-sensitive, which we don't support
        Runtime.getRuntime().load(filename);
    }

    /*
     * Note that there is no substitution for getSecurityManager, but instead getSecurityManager it
     * is intrinsified in SubstrateGraphBuilderPlugins to always return null. This allows better
     * constant folding of SecurityManager code already during static analysis.
     */
    @Substitute
    private static void setSecurityManager(SecurityManager s) {
        if (s != null) {
            /*
             * We deliberately treat this as a non-recoverable fatal error. We want to prevent bugs
             * where an exception is silently ignored by an application and then necessary security
             * checks are not in place.
             */
            throw VMError.shouldNotReachHere("Installing a SecurityManager is not yet supported");
        }
    }

}

@TargetClass(java.lang.StrictMath.class)
@CLibrary(value = "openlibm", requireStatic = false)
final class Target_java_lang_StrictMath {
    // Checkstyle: stop

    @Substitute
    @CFunction(value = "sin", transition = CFunction.Transition.NO_TRANSITION)
    private static native double sin(double a);

    @Substitute
    @CFunction(value = "cos", transition = CFunction.Transition.NO_TRANSITION)
    private static native double cos(double a);

    @Substitute
    @CFunction(value = "tan", transition = CFunction.Transition.NO_TRANSITION)
    private static native double tan(double a);

    @Substitute
    @CFunction(value = "asin", transition = CFunction.Transition.NO_TRANSITION)
    private static native double asin(double a);

    @Substitute
    @CFunction(value = "acos", transition = CFunction.Transition.NO_TRANSITION)
    private static native double acos(double a);

    @Substitute
    @CFunction(value = "atan", transition = CFunction.Transition.NO_TRANSITION)
    private static native double atan(double a);

    @Substitute
    @CFunction(value = "exp", transition = CFunction.Transition.NO_TRANSITION)
    private static native double exp(double a);

    @Substitute
    @CFunction(value = "log", transition = CFunction.Transition.NO_TRANSITION)
    private static native double log(double a);

    @Substitute
    @CFunction(value = "log10", transition = CFunction.Transition.NO_TRANSITION)
    private static native double log10(double a);

    @Substitute
    @CFunction(value = "sqrt", transition = CFunction.Transition.NO_TRANSITION)
    private static native double sqrt(double a);

    @Substitute
    @CFunction(value = "cbrt", transition = CFunction.Transition.NO_TRANSITION)
    private static native double cbrt(double a);

    @Substitute
    @CFunction(value = "remainder", transition = CFunction.Transition.NO_TRANSITION)
    private static native double IEEEremainder(double f1, double f2);

    @Substitute
    @CFunction(value = "atan2", transition = CFunction.Transition.NO_TRANSITION)
    private static native double atan2(double y, double x);

    @Substitute
    @CFunction(value = "pow", transition = CFunction.Transition.NO_TRANSITION)
    private static native double pow(double a, double b);

    @Substitute
    @CFunction(value = "sinh", transition = CFunction.Transition.NO_TRANSITION)
    private static native double sinh(double x);

    @Substitute
    @CFunction(value = "cosh", transition = CFunction.Transition.NO_TRANSITION)
    private static native double cosh(double x);

    @Substitute
    @CFunction(value = "tanh", transition = CFunction.Transition.NO_TRANSITION)
    private static native double tanh(double x);

    @Substitute
    @CFunction(value = "hypot", transition = CFunction.Transition.NO_TRANSITION)
    private static native double hypot(double x, double y);

    @Substitute
    @CFunction(value = "expm1", transition = CFunction.Transition.NO_TRANSITION)
    private static native double expm1(double x);

    @Substitute
    @CFunction(value = "log1p", transition = CFunction.Transition.NO_TRANSITION)
    private static native double log1p(double x);
    // Checkstyle: resume
}

/**
 * We do not have dynamic class loading (and therefore no class unloading), so it is not necessary
 * to keep the complicated code that the JDK uses. However, our simple substitutions have a drawback
 * (not a problem for now):
 * <ul>
 * <li>We do not implement the complicated state machine semantics for concurrent calls to
 * {@link #get} and {@link #remove} that are explained in {@link ClassValue#remove}.
 * </ul>
 */
@TargetClass(java.lang.ClassValue.class)
@Substitute
final class Target_java_lang_ClassValue {

    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Custom, declClass = JavaLangSubstitutions.ClassValueInitializer.class)//
    private final ConcurrentMap<Class<?>, Object> values;

    @Substitute
    private Target_java_lang_ClassValue() {
        values = new ConcurrentHashMap<>();
    }

    /*
     * This method cannot be declared private, because we need the Java compiler to create a
     * invokevirtual bytecode when invoking it.
     */
    @KeepOriginal
    native Object computeValue(Class<?> type);

    @Substitute
    private Object get(Class<?> type) {
        Object result = values.get(type);
        if (result == null) {
            Object newValue = computeValue(type);
            Object oldValue = values.putIfAbsent(type, newValue);
            result = oldValue != null ? oldValue : newValue;
        }
        return result;
    }

    @Substitute
    private void remove(Class<?> type) {
        values.remove(type);
    }
}

@SuppressWarnings("deprecation")
@TargetClass(java.lang.Compiler.class)
final class Target_java_lang_Compiler {
    @Substitute
    static Object command(Object arg) {
        if (arg instanceof Object[]) {
            Object[] args = (Object[]) arg;
            if (args.length > 0) {
                Object arg0 = args[0];
                if (arg0 instanceof String) {
                    String cmd = (String) arg0;
                    Object[] cmdargs = Arrays.copyOfRange(args, 1, args.length);
                    RuntimeSupport rs = RuntimeSupport.getRuntimeSupport();
                    return rs.runCommand(cmd, cmdargs);
                }
            }
        }
        throw new IllegalArgumentException("Argument to java.lang.Compiler.command(Object) must be an Object[] " +
                        "with the first element being a String providing the name of the SVM command to run " +
                        "and subsequent elements being the arguments to the command");
    }

    @SuppressWarnings({"unused"})
    @Substitute
    static boolean compileClass(Class<?> clazz) {
        return false;
    }

    @SuppressWarnings({"unused"})
    @Substitute
    static boolean compileClasses(String string) {
        return false;
    }

    @Substitute
    static void enable() {
    }

    @Substitute
    static void disable() {
    }
}

final class IsSingleThreaded implements Predicate<Class<?>> {
    @Override
    public boolean test(Class<?> t) {
        return !SubstrateOptions.MultiThreaded.getValue();
    }
}

final class IsMultiThreaded implements Predicate<Class<?>> {
    @Override
    public boolean test(Class<?> t) {
        return SubstrateOptions.MultiThreaded.getValue();
    }
}

@TargetClass(className = "java.lang.ApplicationShutdownHooks")
final class Target_java_lang_ApplicationShutdownHooks {

    /**
     * Re-initialize the map of registered hooks, because any hooks registered during native image
     * construction can not survive into the running image. But `hooks` must be initialized to an
     * IdentityHashMap, because 'null' means I am in the middle of shutting down.
     */
    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.NewInstance, declClass = IdentityHashMap.class)//
    private static IdentityHashMap<Thread, Thread> hooks;

    /**
     * Instead of starting all the threads in {@link #hooks}, just run the {@link Runnable}s one
     * after another.
     *
     * We need this substitution in single-threaded mode, where we cannot start new threads but
     * still want to support shutdown hooks. In multi-threaded mode, this substitution is not
     * present, i.e., the original JDK code runs the shutdown hooks in separate threads.
     */
    @Substitute
    @TargetElement(name = "runHooks", onlyWith = IsSingleThreaded.class)
    static void runHooksSingleThreaded() {
        /* Claim all the hooks. */
        final Collection<Thread> threads;
        /* Checkstyle: allow synchronization. */
        synchronized (Target_java_lang_ApplicationShutdownHooks.class) {
            threads = hooks.keySet();
            hooks = null;
        }
        /* Checkstyle: disallow synchronization. */

        /* Run all the hooks, catching anything that is thrown. */
        final List<Throwable> hookExceptions = new ArrayList<>();
        for (Thread hook : threads) {
            try {
                Util_java_lang_ApplicationShutdownHooks.callRunnableOfThread(hook);
            } catch (Throwable ex) {
                hookExceptions.add(ex);
            }
        }
        /* Report any hook exceptions, but do not re-throw them. */
        if (hookExceptions.size() > 0) {
            for (Throwable ex : hookExceptions) {
                ex.printStackTrace(Log.logStream());
            }
        }
    }

    @Alias
    @TargetElement(name = "runHooks", onlyWith = IsMultiThreaded.class)
    static native void runHooksMultiThreaded();

    /**
     * Interpose so that the first time someone adds an ApplicationShutdownHook, I set up a shutdown
     * hook to run all the ApplicationShutdownHooks. Then the rest of this method is copied from
     * {@code ApplicationShutdownHook.add(Thread)}.
     */
    @Substitute
    /* Checkstyle: allow synchronization */
    static synchronized void add(Thread hook) {
        Util_java_lang_ApplicationShutdownHooks.initializeOnce();
        if (hooks == null) {
            throw new IllegalStateException("Shutdown in progress");
        }
        if (hook.isAlive()) {
            throw new IllegalArgumentException("Hook already running");
        }
        if (hooks.containsKey(hook)) {
            throw new IllegalArgumentException("Hook previously registered");
        }
        hooks.put(hook, hook);
    }
    /* Checkstyle: disallow synchronization */
}

class Util_java_lang_ApplicationShutdownHooks {

    /** An initialization flag. */
    private static volatile boolean initialized = false;

    /** A lock to protect the initialization flag. */
    private static ReentrantLock lock = new ReentrantLock();

    public static void initializeOnce() {
        if (!initialized) {
            lock.lock();
            try {
                if (!initialized) {
                    try {
                        /*
                         * Register a shutdown hook.
                         *
                         * Compare this code to the static initializations done in {@link
                         * ApplicationShutdownHooks}.
                         */
                        Target_java_lang_Shutdown.add(1 /* shutdown hook invocation order */,
                                        false /* not registered if shutdown in progress */,
                                        new Runnable() {
                                            @Override
                                            public void run() {
                                                if (SubstrateOptions.MultiThreaded.getValue()) {
                                                    Target_java_lang_ApplicationShutdownHooks.runHooksMultiThreaded();
                                                } else {
                                                    Target_java_lang_ApplicationShutdownHooks.runHooksSingleThreaded();
                                                }
                                            }
                                        });
                    } catch (InternalError ie) {
                        /* Someone else has registered the shutdown hook at slot 2. */
                    } catch (IllegalStateException ise) {
                        /* Too late to register this shutdown hook. */
                    }
                    /* Announce that initialization is complete. */
                    initialized = true;
                }
            } finally {
                lock.unlock();
            }
        }
    }

    @SuppressFBWarnings(value = {"RU_INVOKE_RUN"}, justification = "Do not start a new thread, just call the run method.")
    static void callRunnableOfThread(Thread thread) {
        thread.run();
    }
}

@TargetClass(java.lang.Package.class)
final class Target_java_lang_Package {

    @Alias
    @SuppressWarnings({"unused"})
    Target_java_lang_Package(String name,
                    String spectitle, String specversion, String specvendor,
                    String impltitle, String implversion, String implvendor,
                    URL sealbase, ClassLoader loader) {
    }

    @Substitute
    @TargetElement(onlyWith = JDK8OrEarlier.class)
    static Package getPackage(Class<?> c) {
        if (c.isPrimitive() || c.isArray()) {
            /* Arrays and primitives don't have a package. */
            return null;
        }

        /* Logic copied from java.lang.Package.getPackage(java.lang.Class). */
        String name = c.getName();
        int i = name.lastIndexOf('.');
        if (i != -1) {
            name = name.substring(0, i);
            Target_java_lang_Package pkg = new Target_java_lang_Package(name, null, null, null,
                            null, null, null, null, null);
            return SubstrateUtil.cast(pkg, Package.class);
        } else {
            return null;
        }
    }
}

@TargetClass(java.lang.NullPointerException.class)
final class Target_java_lang_NullPointerException {

    @Substitute
    @TargetElement(onlyWith = JDK14OrLater.class)
    @SuppressWarnings("static-method")
    private String getExtendedNPEMessage() {
        return null;
    }
}

@TargetClass(className = "jdk.internal.loader.BootLoader", onlyWith = JDK11OrLater.class)
final class Target_jdk_internal_loader_BootLoader {

    @Substitute
    static Package getDefinedPackage(String name) {
        if (name != null) {
            Target_java_lang_Package pkg = new Target_java_lang_Package(name, null, null, null,
                            null, null, null, null, null);
            return SubstrateUtil.cast(pkg, Package.class);
        } else {
            return null;
        }
    }

    @Substitute
    private static Class<?> loadClassOrNull(String name) {
        return ClassForNameSupport.forNameOrNull(name, false);
    }

    @SuppressWarnings("unused")
    @Substitute
    private static Class<?> loadClass(Target_java_lang_Module module, String name) {
        return ClassForNameSupport.forNameOrNull(name, false);
    }

    @Substitute
    private static boolean hasClassPath() {
        return true;
    }

    @SuppressWarnings("unused")
    @Substitute
    private static URL findResource(String mn, String name) {
        return ClassLoader.getSystemClassLoader().getResource(name);
    }

    @SuppressWarnings("unused")
    @Substitute
    private static InputStream findResourceAsStream(String mn, String name) {
        return ClassLoader.getSystemClassLoader().getResourceAsStream(name);
    }

    @Substitute
    private static URL findResource(String name) {
        return ClassLoader.getSystemClassLoader().getResource(name);
    }

    @Substitute
    private static Enumeration<URL> findResources(String name) throws IOException {
        return ClassLoader.getSystemClassLoader().getResources(name);
    }
}

/** Dummy class to have a class with the file's name. */
public final class JavaLangSubstitutions {

    @Platforms(Platform.HOSTED_ONLY.class)//
    public static final class ClassValueSupport {
        final Map<ClassValue<?>, Map<Class<?>, Object>> values;

        public ClassValueSupport(Map<ClassValue<?>, Map<Class<?>, Object>> map) {
            values = map;
        }
    }

    static class ClassValueInitializer implements CustomFieldValueComputer {
        @Override
        public Object compute(MetaAccessProvider metaAccess, ResolvedJavaField original, ResolvedJavaField annotated, Object receiver) {
            ClassValueSupport support = ImageSingletons.lookup(ClassValueSupport.class);
            ClassValue<?> v = (ClassValue<?>) receiver;
            Map<Class<?>, Object> map = support.values.get(v);
            assert map != null;
            return map;
        }
    }
}
