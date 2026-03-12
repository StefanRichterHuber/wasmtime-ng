package io.github.stefanrichterhuber.wasmtimejavang;

import java.util.List;

/**
 * Interface for providing a context of imported functions to a WASM module.
 * A context provides a list of functions that can be linked into a WASM module.
 */
public interface WasmContext {
    /**
     * Represents an imported function definition.
     * 
     * @param module      The name of the module providing the function.
     * @param name        The name of the function.
     * @param parameters  The parameter types of the function.
     * @param returnTypes The return types of the function.
     * @param function    The Java implementation of the function.
     */
    public record ImportFunction(String module, String name, List<ValType> parameters, List<ValType> returnTypes,
            WasmtimeFunction function) {
    }

    /**
     * Reprsents an imported memory definition
     */
    public record Importmemory(String module, String name, WasmtimeSharedMemory memory) {
    }

    /**
     * Returns the list of functions provided by this context.
     * 
     * @return A list of ImportFunction objects.
     */
    List<ImportFunction> getImportFunctions();

    /**
     * Returns the list of (shared) Memories provided by this context
     * 
     * @return A list of ImportMemory objects
     */
    List<Importmemory> getMemories();

    /**
     * Returns the name of this context.
     * 
     * @return The name of this context.
     */
    String name();

}
