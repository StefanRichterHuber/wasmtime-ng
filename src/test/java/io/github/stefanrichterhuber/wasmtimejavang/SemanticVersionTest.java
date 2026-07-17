package io.github.stefanrichterhuber.wasmtimejavang;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

public class SemanticVersionTest {
    @Test
    public void parseTest() {
        SemanticVersion v1 = SemanticVersion.parse("1.2.3");
        assertEquals(1, v1.major());
        assertEquals(2, v1.minor());
        assertEquals(3, v1.patch());

        SemanticVersion v2 = SemanticVersion.parse("1.2");
        assertEquals(1, v2.major());
        assertEquals(2, v2.minor());
        assertEquals(0, v2.patch());

        SemanticVersion v3 = SemanticVersion.parse("1");
        assertEquals(1, v3.major());
        assertEquals(0, v3.minor());
        assertEquals(0, v3.patch());

        assertThrows(IllegalArgumentException.class, () -> SemanticVersion.parse(null));
    }

    @Test
    public void compareTest() {
        SemanticVersion v1 = SemanticVersion.parse("1.2.3");
        SemanticVersion v2 = SemanticVersion.parse("1.2.4");
        assertEquals(-1, v1.compareTo(v2));
        assertEquals(1, v2.compareTo(v1));

        SemanticVersion v3 = SemanticVersion.parse("1.2.3");
        SemanticVersion v4 = SemanticVersion.parse("1.2.3");
        assertEquals(0, v3.compareTo(v4));
        assertEquals(v3, v4);

        SemanticVersion v5 = SemanticVersion.parse("1.2.3");
        SemanticVersion v6 = SemanticVersion.parse("1.3.3");
        assertEquals(-1, v5.compareTo(v6));
        assertEquals(1, v6.compareTo(v5));

        SemanticVersion v7 = SemanticVersion.parse("1.2.3");
        SemanticVersion v8 = SemanticVersion.parse("2.2.3");
        assertEquals(-1, v7.compareTo(v8));
        assertEquals(1, v8.compareTo(v7));
    }

}
