package be.cmbsoft.laseroutput;

import java.util.List;

import be.cmbsoft.ilda.IldaFrame;
import be.cmbsoft.ilda.IldaPoint;
import be.cmbsoft.ilda.IldaRenderer;
import org.junit.jupiter.api.Test;

class DwellTest
{
    @Test
    void testBlankDwell()
    {
        IldaRenderer renderer = new IldaRenderer(null, 400, 400);
        renderer.setOptimise(true);
        renderer.beginDraw();
        renderer.stroke(0, 255, 0);
        renderer.line(50, 50, 100, 50);
        renderer.line(100, 100, 50, 100);
        renderer.endDraw();
        IldaFrame       currentFrame = renderer.getCurrentFrame();
        List<IldaPoint> points       = currentFrame.getPoints();
    }

}
