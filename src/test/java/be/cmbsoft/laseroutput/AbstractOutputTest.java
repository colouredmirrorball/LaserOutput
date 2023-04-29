package be.cmbsoft.laseroutput;

import ilda.IldaFrame;
import ilda.IldaRenderer;

public abstract class AbstractOutputTest
{
    protected IldaFrame generateCircle()
    {
        IldaRenderer renderer = new IldaRenderer(null, 200, 200);
        renderer.beginDraw();
        renderer.stroke(renderer.color(255, 0, 0));
        renderer.ellipse(100, 100, 50, 50);
        renderer.endDraw();
        return renderer.getCurrentFrame();
    }

    protected IldaFrame generateSquare()
    {
        IldaRenderer renderer = new IldaRenderer(null, 200, 200);
        renderer.setOptimise(false);
        renderer.beginDraw();
        renderer.stroke(renderer.color(0, 255, 0));
        renderer.rect(50, 50, 100, 100);
        renderer.endDraw();
        return renderer.getCurrentFrame();
    }

}
