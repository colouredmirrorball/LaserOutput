package be.cmbsoft.laseroutput;

import be.cmbsoft.ilda.IldaFrame;
import be.cmbsoft.ilda.IldaRenderer;
import processing.core.PApplet;

public abstract class AbstractOutputTest
{
    protected IldaFrame generateCircle()
    {
        IldaRenderer renderer = new IldaRenderer(null, 200, 200);
        renderer.beginDraw();
        renderer.stroke(renderer.color(255, 0, 0));
        renderer.ellipse(100, 100, 50, 50);
        renderer.stroke(renderer.color(0, 255, 0));
        renderer.ellipse(120, 80, 30, 30);
        renderer.stroke(renderer.color(0, 0, 255));
        renderer.ellipse(70, 130, 25, 25);
        renderer.stroke(renderer.color(255, 0, 255));
        renderer.ellipse(100, 130, 125, 25);
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

    protected IldaFrame generateTriangle() {
        IldaRenderer renderer = new IldaRenderer(null, 200, 200);
        renderer.setOptimise(false);
        renderer.beginDraw();
        renderer.stroke(renderer.color(0, 0, 255));
        renderer.triangle(50, 150, 100, 50, 150, 150);
        renderer.endDraw();
        return renderer.getCurrentFrame();
    }

    protected IldaFrame generateText() {
        PApplet applet = new PApplet();
        PApplet.runSketch(new String[]{getClass().getCanonicalName()}, applet);
        IldaRenderer renderer = new IldaRenderer(applet, 200, 200);
        renderer.setOptimise(false);
        renderer.beginDraw();
        renderer.stroke(renderer.color(0, 0, 255));
        renderer.textSize(50);
        renderer.text("This is a long text that should overflow the bounds", 10, 100);
        renderer.endDraw();
        return renderer.getCurrentFrame();
    }

}
