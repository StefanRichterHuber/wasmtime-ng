package io.github.stefanrichterhuber.wasmtimejavang;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

public class WasmtimeMemoryTest {
    private static final String wat = """
                     (module
              (memory (export "memory") 2 3)

              (func (export "size") (result i32) (memory.size))
              (func (export "load") (param i32) (result i32)
                (i32.load8_s (local.get 0))
              )
              (func (export "store") (param i32 i32)
                (i32.store8 (local.get 0) (local.get 1))
              )

              (data (i32.const 0x1000) "\\01\\02\\03\\04")
            )

                     """;

    @Test
    public void testManipulateMemory() throws Exception {
        try (
                WasmtimeEngine engine = new WasmtimeEngine();
                WasmtimeModule module = new WasmtimeModule(engine, wat);
                WasmtimeStore store = new WasmtimeStore(engine);
                WasmtimeLinker linker = new WasmtimeLinker(engine, store);

        ) {
            try (WasmtimeInstance instance = new WasmtimeInstance(store, module, linker)) {
                WasmtimeMemory mem = instance.getMemory("memory");

                // Read predefined data from 0x1000
                byte[] result = mem.read(0x1000, 4);
                assertNotNull(result);
                assertEquals(4, result.length);
                assertEquals(1, result[0]); // 0x1000
                assertEquals(2, result[1]);// 0x1001
                assertEquals(3, result[2]);// 0x1002
                assertEquals(4, result[3]);// 0x1003

                // Mutate predefined data
                mem.write(0x1003, (byte) 5);

                // Read back mutated data
                byte[] result2 = mem.read(0x1003, 1);
                assertEquals(1, result2.length);
                assertEquals((byte) 5, result2[0]); // 0x1003

                // Higher level memory interactions. Usually use some kind of native alloc /
                // dealloc allocate memory locations prior to use
                mem.writeCString(0x1000, "Hello", StandardCharsets.UTF_8);
                assertEquals("Hello", mem.readCString(0x1000, StandardCharsets.UTF_8));

                mem.writeString(0x1000, "World", StandardCharsets.UTF_8);
                assertEquals("World", mem.readString(0x1000, "Hello".getBytes().length, StandardCharsets.UTF_8));

                mem.writeLong(0x1000, Long.MAX_VALUE);
                assertEquals(Long.MAX_VALUE, mem.readLong(0x1000));

                mem.writeInt(0x1000, Integer.MAX_VALUE);
                assertEquals(Integer.MAX_VALUE, mem.readInt(0x1000));

                mem.writeShort(0x1000, Short.MAX_VALUE);
                assertEquals(Short.MAX_VALUE, mem.readShort(0x1000));

                mem.write(0x1000, Byte.MAX_VALUE);
                assertEquals(Byte.MAX_VALUE, mem.readByte(0x1000));

                // Grow to memory to have another page
                mem.grow(1);

                try {
                    WasmtimeMemory nonExistingMemory = instance.getMemory("non-existing-memory");
                    nonExistingMemory.read(0x1000, 4);
                    fail("Must fail, since memory does not exist");
                } catch (RuntimeException e) {
                    // Expected
                }
            }

        }
    }
}
