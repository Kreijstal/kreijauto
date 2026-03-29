package bsh;

/** Stub — class generation not supported without ASM on Android. */
public final class ClassGenerator {
    private static ClassGenerator cg;

    public static ClassGenerator getClassGenerator() {
        if (cg == null) cg = new ClassGenerator();
        return cg;
    }

    public Class generateClass(String name, Modifiers modifiers, Class[] interfaces,
            Class superClass, BSHBlock block, boolean isInterface,
            CallStack callstack, Interpreter interpreter) throws EvalError {
        throw new EvalError("Class generation not supported (no ASM)", null, null);
    }

    public Object invokeSuperclassMethod(BshClassManager bcm, Object instance,
            String methodName, Object[] args)
            throws UtilEvalError, ReflectError, java.lang.reflect.InvocationTargetException {
        throw new UtilEvalError("Class generation not supported (no ASM)");
    }

    public void setInstanceNameSpaceParent(Object instance, String className, NameSpace parent) {
        // no-op
    }

    public static Class generateClassImpl(String name, Modifiers modifiers, Class[] interfaces,
            Class superClass, BSHBlock block, boolean isInterface,
            CallStack callstack, Interpreter interpreter) throws EvalError {
        throw new EvalError("Class generation not supported (no ASM)", null, null);
    }
}
