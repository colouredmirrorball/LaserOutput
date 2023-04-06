package be.cmbsoft.laseroutput;

import org.junit.jupiter.api.Test;

import ilda.IldaFrame;
import ilda.IldaRenderer;

class LsxOscOutputTest
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

    private IldaFrame generateCircle()
    {
        IldaRenderer renderer = new IldaRenderer(null, 200, 200);
        renderer.beginDraw();
        renderer.stroke(renderer.color(255, 0, 0));
        renderer.ellipse(100, 100, 50, 50);
        renderer.endDraw();
        return renderer.getCurrentFrame();
    }

    private IldaFrame generateSquare()
    {
        IldaRenderer renderer = new IldaRenderer(null, 200, 200);
        renderer.beginDraw();
        renderer.stroke(renderer.color(0, 255, 0));
        renderer.rect(50, 50, 100, 100);
        renderer.endDraw();
        return renderer.getCurrentFrame();
    }

}