package io.github.stefanrichterhuber.witcodegen;

import java.util.List;

import io.github.stefanrichterhuber.witparser.WitFunction;
import io.github.stefanrichterhuber.witparser.WitInterface;
import io.github.stefanrichterhuber.witparser.WitParam;
import io.github.stefanrichterhuber.witparser.WitType;

/**
 * Generates an abstract {@code WasmComponentContext} implementation from a
 * single {@link WitInterface}: all of the interface-name/version/
 * {@code getImportFunctions()} plumbing is filled in concretely, and one
 * abstract, typed method is emitted per WIT function -- callers extend the
 * generated class and only implement those.
 * <br>
 * Only the primitive types {@link WitType} covers are supported; this
 * mirrors the equivalent restriction in the native {@code wit-parser}
 * binding this plugin is built on.
 */
public final class WitCodeGenerator {

    private WitCodeGenerator() {
    }

    /**
     * The generated class name for a given WIT interface, e.g.
     * {@code "AbstractGreetContext"} for {@code "my:custom/greet"}.
     *
     * @param interfaceName Fully-qualified WIT interface name, as returned by
     *                       {@code WitInterface.name()}.
     * @return The class name to generate.
     */
    public static String className(String interfaceName) {
        return "Abstract" + pascalCase(simpleName(interfaceName)) + "Context";
    }

    /**
     * Generates the Java source of an abstract {@code WasmComponentContext}
     * implementation for the given WIT interface.
     *
     * @param targetPackage Package the generated class is placed in.
     * @param iface         The interface to generate a base class for.
     * @return The complete Java source of the generated class, including
     *         package declaration and imports.
     */
    public static String generate(String targetPackage, WitInterface iface) {
        String className = className(iface.name());
        String simpleName = simpleName(iface.name());

        StringBuilder importFunctions = new StringBuilder();
        StringBuilder abstractMethods = new StringBuilder();
        StringBuilder implMethods = new StringBuilder();

        List<WitFunction> functions = iface.functions();
        for (int i = 0; i < functions.size(); i++) {
            WitFunction function = functions.get(i);
            String methodName = camelCase(function.name());
            String implName = methodName + "Impl";

            importFunctions.append("                new ComponentImportFunction(versioned, \"")
                    .append(function.name())
                    .append("\", this::")
                    .append(implName)
                    .append(")");
            importFunctions.append(i < functions.size() - 1 ? ",\n" : "\n");

            String returnType = function.result().map(WitCodeGenerator::javaType).orElse("void");
            StringBuilder paramList = new StringBuilder("WasmtimeComponentInstance instance");
            for (WitParam param : function.params()) {
                paramList.append(", ").append(javaType(param.type())).append(' ')
                        .append(camelCase(param.name()));
            }
            abstractMethods.append("    protected abstract ").append(returnType).append(' ')
                    .append(methodName).append('(').append(paramList).append(");\n\n");

            StringBuilder implBody = new StringBuilder();
            List<WitParam> params = function.params();
            for (int p = 0; p < params.size(); p++) {
                WitParam param = params.get(p);
                implBody.append("        ").append(javaType(param.type())).append(' ')
                        .append(camelCase(param.name())).append(" = (")
                        .append(boxedType(param.type())).append(") args[").append(p).append("];\n");
            }
            StringBuilder callArgs = new StringBuilder("instance");
            for (WitParam param : params) {
                callArgs.append(", ").append(camelCase(param.name()));
            }
            if (function.result().isPresent()) {
                implBody.append("        return new Object[] { ").append(methodName).append('(')
                        .append(callArgs).append(") };\n");
            } else {
                implBody.append("        ").append(methodName).append('(').append(callArgs)
                        .append(");\n        return new Object[0];\n");
            }
            implMethods.append("    private Object[] ").append(implName)
                    .append("(WasmtimeComponentInstance instance, Object... args) {\n")
                    .append(implBody).append("    }\n\n");
        }

        return """
                package %s;

                import java.util.List;
                import java.util.Set;

                import io.github.stefanrichterhuber.wasmtimejavang.SemanticVersion;
                import io.github.stefanrichterhuber.wasmtimejavang.WasmComponentContext;
                import io.github.stefanrichterhuber.wasmtimejavang.WasmtimeComponentInstance;

                /**
                 * Generated from WIT interface "%s" by wit-codegen-maven-plugin.
                 * Do not edit directly -- extend this class and implement the
                 * abstract methods instead.
                 */
                public abstract class %s implements WasmComponentContext {
                    private static final String INTERFACE = "%s";

                    private SemanticVersion version = DEFAULT_VERSION;

                    @Override
                    public String name() {
                        return "%s";
                    }

                    @Override
                    public List<ComponentImportFunction> getImportFunctions() {
                        String versioned = INTERFACE + "@" + version;
                        return List.of(
                %s            );
                    }

                    @Override
                    public List<ComponentImportResource> getImportResources() {
                        return List.of();
                    }

                    @Override
                    public Set<String> getProvidedInterfaces() {
                        return Set.of(INTERFACE);
                    }

                    @Override
                    public WasmComponentContext withVersion(SemanticVersion version) {
                        this.version = version;
                        return this;
                    }

                    @Override
                    public SemanticVersion getVersion() {
                        return version;
                    }

                %s%s}
                """.formatted(targetPackage, iface.name(), className, iface.name(), simpleName,
                importFunctions, abstractMethods, implMethods);
    }

    private static String simpleName(String interfaceName) {
        int slash = interfaceName.lastIndexOf('/');
        return slash < 0 ? interfaceName : interfaceName.substring(slash + 1);
    }

    private static String pascalCase(String kebabCase) {
        StringBuilder result = new StringBuilder();
        for (String part : kebabCase.split("-")) {
            if (!part.isEmpty()) {
                result.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
            }
        }
        return result.toString();
    }

    private static String camelCase(String kebabCase) {
        String pascal = pascalCase(kebabCase);
        return pascal.isEmpty() ? pascal
                : Character.toLowerCase(pascal.charAt(0)) + pascal.substring(1);
    }

    private static String javaType(WitType type) {
        return switch (type) {
            case BOOL -> "boolean";
            case S8, U8, S16, U16, S32, U32 -> "int";
            case S64, U64 -> "long";
            case F32 -> "float";
            case F64 -> "double";
            case CHAR -> "char";
            case STRING -> "String";
        };
    }

    private static String boxedType(WitType type) {
        return switch (type) {
            case BOOL -> "Boolean";
            case S8, U8, S16, U16, S32, U32 -> "Integer";
            case S64, U64 -> "Long";
            case F32 -> "Float";
            case F64 -> "Double";
            case CHAR -> "Character";
            case STRING -> "String";
        };
    }
}
