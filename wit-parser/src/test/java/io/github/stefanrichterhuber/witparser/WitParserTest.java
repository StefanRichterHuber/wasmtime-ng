package io.github.stefanrichterhuber.witparser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class WitParserTest {

    @Test
    void parsesGreetInterface() throws IOException {
        Path witFile = copyResourceToTempFile("/wit/greet.wit");

        List<WitInterface> interfaces = WitParser.parse(witFile);

        assertEquals(1, interfaces.size());
        WitInterface greet = interfaces.get(0);
        assertEquals("my:custom/greet", greet.name());
        assertEquals(2, greet.functions().size());

        WitFunction hello = greet.functions().get(0);
        assertEquals("hello", hello.name());
        assertEquals(List.of(new WitParam("name", WitType.STRING)), hello.params());
        assertEquals(Optional.of(WitType.STRING), hello.result());

        WitFunction add = greet.functions().get(1);
        assertEquals("add", add.name());
        assertEquals(
                List.of(new WitParam("a", WitType.U32), new WitParam("b", WitType.U32)),
                add.params());
        assertEquals(Optional.of(WitType.U32), add.result());
    }

    @Test
    void throwsOnUnknownFile() {
        assertTrue(org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> WitParser.parse(Path.of("/no/such/file.wit"))).getMessage()
                .contains("no/such/file.wit"));
    }

    private static Path copyResourceToTempFile(String resource) throws IOException {
        Path tempFile = Files.createTempFile("witparsertest", ".wit");
        tempFile.toFile().deleteOnExit();
        try (InputStream is = WitParserTest.class.getResourceAsStream(resource)) {
            Files.copy(is, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        return tempFile;
    }
}
