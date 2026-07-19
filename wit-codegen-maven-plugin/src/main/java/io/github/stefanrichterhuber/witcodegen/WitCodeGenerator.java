package io.github.stefanrichterhuber.witcodegen;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import io.github.stefanrichterhuber.witparser.WitFunction;
import io.github.stefanrichterhuber.witparser.WitInterface;
import io.github.stefanrichterhuber.witparser.WitParam;
import io.github.stefanrichterhuber.witparser.WitTypeKind;
import io.github.stefanrichterhuber.witparser.WitValueType;

/**
 * Generates an abstract {@code WasmComponentContext} implementation from a
 * single {@link WitInterface}: all of the interface-name/version/
 * {@code getImportFunctions()}/{@code getImportResources()} plumbing is
 * filled in concretely, and one abstract, typed method is emitted per WIT
 * function (plus one abstract destructor per resource) -- callers extend the
 * generated class and only implement those.
 * <br>
 * Every {@link WitTypeKind} maps to a fixed Java type (see {@link #javaType}):
 * nested structure (record field types, variant payload types, etc.) isn't
 * modeled in the generated signature, matching how the hand-written
 * {@code wasip2} contexts already represent these shapes as opaque
 * {@code Map}/{@code WitVariant}/etc. wrappers cast by the implementer.
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

        StringBuilder importFunctionEntries = new StringBuilder();
        StringBuilder abstractMethods = new StringBuilder();
        StringBuilder implMethods = new StringBuilder();

        List<WitFunction> functions = iface.functions();
        for (int i = 0; i < functions.size(); i++) {
            WitFunction function = functions.get(i);
            String methodName = methodName(function.name());
            String implName = methodName + "Impl";

            importFunctionEntries.append("                new ComponentImportFunction(versioned(), \"")
                    .append(function.name())
                    .append("\", this::")
                    .append(implName)
                    .append(")");
            importFunctionEntries.append(i < functions.size() - 1 ? ",\n" : "\n");

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
                        .append(castType(param.type())).append(") args[").append(p).append("];\n");
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

        StringBuilder resourceDestructors = new StringBuilder();
        StringBuilder importResourceEntries = new StringBuilder();
        List<String> resources = iface.resources();
        for (int i = 0; i < resources.size(); i++) {
            String resourceName = resources.get(i);
            String pascalResourceName = pascalCase(resourceName);
            resourceDestructors.append("    protected abstract void drop").append(pascalResourceName)
                    .append("(int rep);\n\n");
            importResourceEntries.append("                new ComponentImportResource(versioned(), \"")
                    .append(resourceName).append("\", this::drop").append(pascalResourceName).append(")");
            importResourceEntries.append(i < resources.size() - 1 ? ",\n" : "\n");
        }
        String importResourcesBody = resources.isEmpty()
                ? "List.of()"
                : "List.of(\n" + importResourceEntries + "            )";

        String extraImports = String.join("\n", requiredImports(iface));

        return """
                package %s;

                import java.util.List;
                import java.util.Set;
                %s

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

                    private String versioned() {
                        return INTERFACE + "@" + version;
                    }

                    @Override
                    public List<ComponentImportFunction> getImportFunctions() {
                        return List.of(
                %s            );
                    }

                    @Override
                    public List<ComponentImportResource> getImportResources() {
                        return %s;
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

                %s%s%s}
                """.formatted(targetPackage, extraImports, iface.name(), className, iface.name(),
                simpleName, importFunctionEntries, importResourcesBody, abstractMethods,
                resourceDestructors, implMethods);
    }

    /** Extra imports beyond the always-present {@code java.util.{List,Set}}, only for the WIT
     * shapes this interface's functions actually use. */
    private static Set<String> requiredImports(WitInterface iface) {
        Set<WitTypeKind> kinds = new LinkedHashSet<>();
        for (WitFunction function : iface.functions()) {
            for (WitParam param : function.params()) {
                kinds.add(param.type().kind());
            }
            function.result().ifPresent(t -> kinds.add(t.kind()));
        }

        Set<String> imports = new TreeSet<>();
        if (kinds.contains(WitTypeKind.OPTION)) {
            imports.add("import java.util.Optional;");
        }
        if (kinds.contains(WitTypeKind.RECORD)) {
            imports.add("import java.util.Map;");
        }
        if (kinds.contains(WitTypeKind.VARIANT)) {
            imports.add("import io.github.stefanrichterhuber.wasmtimejavang.component.WitVariant;");
        }
        if (kinds.contains(WitTypeKind.ENUM)) {
            imports.add("import io.github.stefanrichterhuber.wasmtimejavang.component.WitEnum;");
        }
        if (kinds.contains(WitTypeKind.RESULT)) {
            imports.add("import io.github.stefanrichterhuber.wasmtimejavang.component.WitResult;");
        }
        if (kinds.contains(WitTypeKind.RESOURCE)) {
            imports.add("import io.github.stefanrichterhuber.wasmtimejavang.component.WitResource;");
        }
        return imports;
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

    /**
     * Converts a WIT function name -- possibly the bracketed form
     * {@code wit-parser} itself produces for resource methods (e.g.
     * {@code "[method]input-stream.read"}) -- into a Java method name,
     * folding the resource name in (e.g. {@code "inputStreamRead"}) so
     * same-named methods on different resources in one interface don't
     * collide, matching the hand-written {@code wasip2} contexts' own
     * naming convention.
     */
    private static String methodName(String witFuncName) {
        String stripped = witFuncName;
        int bracketEnd = stripped.indexOf(']');
        if (bracketEnd >= 0) {
            stripped = stripped.substring(bracketEnd + 1);
        }
        return camelCase(stripped.replace('.', '-'));
    }

    private static String javaType(WitValueType type) {
        return switch (type.kind()) {
            case BOOL -> "boolean";
            case S8, U8, S16, U16, S32, U32 -> "int";
            case S64, U64 -> "long";
            case F32 -> "float";
            case F64 -> "double";
            case CHAR -> "char";
            case STRING -> "String";
            case LIST_U8 -> "byte[]";
            case LIST -> "List<Object>";
            case OPTION -> "Optional<Object>";
            case RESULT -> "WitResult";
            case TUPLE -> "Object[]";
            case RECORD -> "Map<String, Object>";
            case VARIANT -> "WitVariant";
            case ENUM -> "WitEnum";
            case FLAGS -> "Set<String>";
            case RESOURCE -> "WitResource";
        };
    }

    /** The type an {@code (X) args[i]} cast expression should use -- the boxed wrapper for
     * primitives (autoboxing means {@code args[i]} is never actually a primitive), the same
     * type as {@link #javaType} for everything else. */
    private static String castType(WitValueType type) {
        return switch (type.kind()) {
            case BOOL -> "Boolean";
            case S8, U8, S16, U16, S32, U32 -> "Integer";
            case S64, U64 -> "Long";
            case F32 -> "Float";
            case F64 -> "Double";
            case CHAR -> "Character";
            default -> javaType(type);
        };
    }
}
