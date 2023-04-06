package be.cmbsoft.laseroutput;

import org.junit.jupiter.api.Test;

import ilda.IldaFrame;

class LsxOscOutputTest extends AbstractOutputTest
{

    @Test
    void sendCircle()
    {

        IldaFrame    ildaFrame = generateCircle();
        LsxOscOutput output    = new LsxOscOutput(1, 10, "127.0.0.1", 10000);
        output.project(ildaFrame.getPoints());

    }

    @Test
    void sendSquare()
    {

        IldaFrame    ildaFrame = generateSquare();
        LsxOscOutput output    = new LsxOscOutput(1, 11, "127.0.0.1", 10000);
        output.project(ildaFrame.getPoints());

    }



}