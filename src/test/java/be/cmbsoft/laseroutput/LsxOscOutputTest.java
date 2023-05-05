package be.cmbsoft.laseroutput;

import be.cmbsoft.ilda.IldaFrame;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class LsxOscOutputTest extends AbstractOutputTest
{

    @Test
    void sendCircle()
    {

        IldaFrame    ildaFrame = generateCircle();
        LsxOscOutput output    = new LsxOscOutput(1, 10, "127.0.0.1", 10000);
        Assertions.assertDoesNotThrow(() -> output.project(ildaFrame));

    }

    @Test
    void sendSquare()
    {

        IldaFrame    ildaFrame = generateSquare();
        LsxOscOutput output    = new LsxOscOutput(1, 10, "127.0.0.1", 10000);
        Assertions.assertDoesNotThrow(() -> output.project(ildaFrame));

    }

    @Test
    void sendTriangle() {

        IldaFrame ildaFrame = generateTriangle();
        LsxOscOutput output = new LsxOscOutput(1, 10, "127.0.0.1", 10000);
        Assertions.assertDoesNotThrow(() -> output.project(ildaFrame));

    }

    @Test
    void sendText() {

        IldaFrame ildaFrame = generateText();
        LsxOscOutput output = new LsxOscOutput(1, 10, "127.0.0.1", 10000);
        Assertions.assertDoesNotThrow(() -> output.project(ildaFrame));

    }


}