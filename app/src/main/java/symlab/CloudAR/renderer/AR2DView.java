package symlab.CloudAR.renderer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public class AR2DView extends View {
    private Paint paintWordGray, paintWordRed, paintWordGreen;
    private int status = 0;
    private int pulseCount = 31;
    private long t0, t1, tl;
    private int orange = Color.rgb(234, 116, 0);

    public AR2DView(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);

        paintWordGray = new Paint();
        paintWordGray.setStyle(Paint.Style.STROKE);
        paintWordGray.setStrokeWidth(5);
        paintWordGray.setColor(orange);
        paintWordGray.setTextAlign(Paint.Align.LEFT);
        paintWordGray.setTextSize(80);

        paintWordRed = new Paint();
        paintWordRed.setStyle(Paint.Style.FILL);
        paintWordRed.setStrokeWidth(5);
        paintWordRed.setColor(orange);
        paintWordRed.setTextAlign(Paint.Align.RIGHT);
        paintWordRed.setTextSize(60);

        paintWordGreen = new Paint();
        paintWordGreen.setStyle(Paint.Style.FILL);
        paintWordGreen.setStrokeWidth(5);
        paintWordGreen.setColor(orange);
        paintWordGreen.setTextAlign(Paint.Align.CENTER);
        paintWordGreen.setTextSize(120);
    }

    public void setStatus(int status) {
        this.status = status;
        if(status == 1) t0 = System.currentTimeMillis();
        else if(status == 2) pulseCount = 0;
    }

    public void pulse() {
        pulseCount++;
        if(status == 1) {
            this.invalidate();
        } else if(pulseCount == 30) {
            this.status = 3;
            this.invalidate();
        }
    }

    @Override
    protected void onDraw(final Canvas canvas) {
        super.onDraw(canvas);
        int width = canvas.getWidth();
        int height = canvas.getHeight();

        canvas.drawText("With Mobile Edge Computing ", 100, 100, paintWordGray);

        switch (status) {
            case 0:
                canvas.drawText("Tap On Screen For Recognition", width/2, height/2, paintWordGreen);
                break;
            case 1:
                t1 = System.currentTimeMillis();
                tl = t1 - t0;
                canvas.drawText("Identifying Poster Remotely", width/2, height/2, paintWordGreen);
                canvas.drawText("" + tl/1000.0f + "seconds", width/2, height/2 + 100, paintWordGreen);
                canvas.drawText("Please Keep Poster In View!", width - 100, height - 50, paintWordRed);
                break;
            case 2:
                t1 = System.currentTimeMillis();
                tl = t1 - t0;
                canvas.drawText("Poster Identified In: " + tl/1000.0f + "s", width/2, height/2, paintWordGreen);
                break;
            case 3:
                canvas.drawText("Poster Identified In: " + tl/1000.0f + "s", width - 100, height - 50, paintWordRed);
                break;
            case 4:
                canvas.drawText("No Poster In View, Please Tap Again", width/2, height/2, paintWordGreen);
                break;
            case 5:
                canvas.drawText("Network Issue Encountered, Please Tap Again", width/2, height/2, paintWordGreen);
                break;
            default:
                break;
        }
    }
}