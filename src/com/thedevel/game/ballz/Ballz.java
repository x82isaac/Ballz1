/**
 *
 * Ballz
 *
 * http://www.thedevel.com/
 * Copyright (C) 2013, Michael Allen C. Isaac.  All rights reserved.
 * 
 * 109
 */


package com.thedevel.game.ballz;

import java.util.ArrayList;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

//import android.R;
import com.thedevel.game.ballz.R;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Debug;
import android.os.PowerManager;
import android.os.Vibrator;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;


//this class extends KeyEvent.Callback so that we can intercept key presses
@SuppressWarnings("unused")
public class Ballz extends Activity implements KeyEvent.Callback, SensorEventListener
{

    /**
     * used to represent the height and width of the screen,
     * so that we can define boundries for the on-screen actor.
     * it will also be used to resize the background image.
     * 
     */
    
    private static float         SCR_H;
    private static float         SCR_W;
    
    
    /**
     * this is the size (in pixels) of the floor as it is drawn on
     * the background image, if this values needs to change its likely
     * because we changed the background image.
     */
    
    private static float         SCR_BG_FLOOR                = (float)25;    

    /** states of the game */

    private final int               APP_STATE_PAUSE         = 0;
    private final int               APP_STATE_PLAY          = 1;
    private final int               APP_STATE_DEAD          = 2;
    private final int               APP_STATE_QUIT          = 4;
    private final int               APP_STATE_RESTART       = 8;

    /** drop a new ball in play for each APP_DROP_ON_SCORE */
    private int                     APP_DROP_ON_SCORE       = 10;
    
    /** our current score */
    private int                     APP_SCORE               = 0;
    
    /** what game state are we presently in */
    private int                     APP_GAME_STATE          = APP_STATE_PAUSE;
    
    
    private BallzView               bzObj_View;

    private PowerManager            bzObj_PowerMgr;
    private PowerManager.WakeLock   bzObj_PowerMgr_WakeLock;
    
    private Sensor                  bzObj_Sensor;
    private SensorManager           bzObj_SensorMgr;

    private boolean                 bzObj_bHaveSensor;

    private Vibrator                bzObj_Vibrator;
    
    //private class BallzView extends SurfaceView implements SurfaceHolder.Callback, SensorEventListener
    private class BallzView extends SurfaceView implements SurfaceHolder.Callback
    {
        private DisplayMetrics     bzv_Screen;
        private SurfaceHolder      bzv_Holder; 
        private Canvas             bzv_Canvas;
        private boolean            bzv_bHaveSurface;

        private C_Actor            bzv_Actor = new C_Actor();
        private ArrayList<C_Ball>  bzv_BallArray = new ArrayList<C_Ball>();
        
        private C_Label            bzv_lblScore = new C_Label();
        private C_Label            bzv_lblDead = new C_Label();
        
        private C_ProgressBar      bzv_HealthMeter = new C_ProgressBar();
        
        private Paint              bzv_pObj = new Paint();
        private Paint              bzv_pClear = new Paint();
        
        private Timer              bzv_DropTimer = new Timer();

        /**
         *  this code needs to be updated so that we can change the size of the background image
         *  dynamically.  I would like to load an svg resource that will be sized automatically
         *  to the screen size.  the goal would be to provide a pixel perfect background image
         *  using a single resource.  There will also need to be a static image that will be
         *  overlaid on to the background image for the floor.  
         */
        
        //private Bitmap            bzv_bImagePlay = BitmapFactory.decodeResource(getResources(), R.drawable.background);
        private Bitmap            bzv_bImagePlay = null;
        
        /**
         * these are images which are displayed based on the game state, for example if you're Dead of the game is Paused.
         */
        
        private Bitmap             bzv_bImageDead = BitmapFactory.decodeResource(getResources(), R.drawable.lbl_paused);
        private Bitmap             bzv_bImagePause = BitmapFactory.decodeResource(getResources(), R.drawable.lbl_paused);
        
        /** what is this? */
        private float             bzv_BackgroundX;

        
        /** constructor */
        public BallzView(Context context)
        {
            super(context);
            
            bzv_Holder = this.getHolder();
            bzv_Holder.addCallback(this);

            /** http://tkcodesharing.blogspot.com/2009/01/android-3d-graphics-and-surfaceview.html */
            //this maybe a deprecated way to get surface type.
            try
            {
                bzv_Holder.setType(android.view.SurfaceHolder.SURFACE_TYPE_GPU);
            } catch(Exception e) {
                try
                {
                    bzv_Holder.setType(android.view.SurfaceHolder.SURFACE_TYPE_HARDWARE);
                } catch(Exception e2) {
                    try
                    {
                        bzv_Holder.setType(android.view.SurfaceHolder.SURFACE_TYPE_NORMAL);
                    } catch(Exception e3) {
                    }
                }
            }
            
            bzv_Screen = new DisplayMetrics();

            getWindowManager().getDefaultDisplay().getMetrics(bzv_Screen);

            SCR_W = bzv_Screen.widthPixels;
            SCR_H = bzv_Screen.heightPixels;
            
            SCR_BG_FLOOR = (SCR_H - ((float)25));
           
            //bzv_BackgroundX = SCR_H - ((float)(bzv_bImagePlay.getHeight()));

            bzv_lblScore.X = 15;
            bzv_lblScore.Y = 30;
            bzv_lblScore.Txt = "Score: 0";
            bzv_lblScore.PaintStyle.setTextSize(15);
            bzv_lblScore.PaintStyle.setAntiAlias(true);
            
            //bzv_lblScore.PaintStyle.setColor(Color.BLACK);
            bzv_lblScore.PaintStyle.setColor(Color.WHITE);

            bzv_lblDead.X = (int)(SCR_W / 2);
            bzv_lblDead.Y = (int)(SCR_H - 50);
            bzv_lblDead.PaintStyle.setAntiAlias(true);
            bzv_lblDead.PaintStyle.setTextSize(45);
            bzv_lblDead.PaintStyle.setTextAlign(Paint.Align.CENTER);
            
            //bzv_lblDead.PaintStyle.setColor(Color.BLACK);
            bzv_lblDead.PaintStyle.setColor(Color.WHITE);
            
            bzv_HealthMeter.X = (SCR_W - bzv_HealthMeter.W) - 15;
            bzv_HealthMeter.Y = 15;
            bzv_HealthMeter.setValue(bzv_Actor.Health);
            
            bzv_Actor.X = (SCR_W / 2);
            bzv_Actor.Y = (SCR_BG_FLOOR - (bzv_Actor.H));
            
            bzv_DropTimer.schedule(new C_DropBall(), getRandomInt(1000, 2000));
            bzv_DropTimer.schedule(new C_DropBall(), getRandomInt(1000, 2000));
            
            StartMe();
        }

        public void StartMe()
        {
            new MainAsyncTask().execute(null, null, null);
        }
        
        public void ResetPlay()
        {
            bzv_Actor.X = (SCR_W / 2);
            bzv_Actor.Y = (SCR_BG_FLOOR - (bzv_Actor.H));
            bzv_Actor.Health = 100;
            
            bzv_BallArray.clear();
            
            bzv_DropTimer.schedule(new C_DropBall(), getRandomInt(1000, 2000));
            bzv_DropTimer.schedule(new C_DropBall(), getRandomInt(1000, 2000));

            APP_DROP_ON_SCORE = 10;
            APP_SCORE = 0;
            APP_GAME_STATE = APP_STATE_PLAY;

            bzv_lblScore.Txt = String.format("Score: %d", APP_SCORE);
            
            bzv_HealthMeter.setValue(bzv_Actor.Health);

            StartMe();
            RenderLoop();
        }
        
        private class C_DropBall extends TimerTask
        {
            @Override
            public void run() {
                bzv_BallArray.add(new C_Ball());
            }
        }
        
        
        
        public class MainAsyncTask extends AsyncTask<Object, Object, Object>
        {

            protected void onPreExecute ()
            {
                return;
            }
            
            protected synchronized Object doInBackground(Object... arg0) {
                while (APP_GAME_STATE == APP_STATE_PLAY)
                {
                    /** drop a new ball on the screen */
                    if ((APP_SCORE > APP_DROP_ON_SCORE))
                    {
                        /** wait 1-2 seconds before dropping the ball new ball */
                        bzv_DropTimer.schedule(new C_DropBall(), getRandomInt(1000, 2000));

                        /** set the score or level that will trigger the next ball drop */
                        APP_DROP_ON_SCORE += 10;
                    }

                    //bzv_Actor.Update();
                    
                    for (int x = 0; (x < bzv_BallArray.size()); x++)
                    {
                        
                        /** score!  delete that ball and drop a new one */
                        if (bzv_BallArray.get(x).X > SCR_W)
                        {
                            APP_SCORE += 1;
                            
                            bzv_BallArray.remove(x);
                            bzv_DropTimer.schedule(new C_DropBall(), getRandomInt(1000, 2000));
                            
                            /** update the score text */
                            bzv_lblScore.Txt = String.format("Score: %d", APP_SCORE);
                            continue;
                        }
                        
                        if(bzv_BallArray.get(x).hazCollision(bzv_Actor))
                        {
                            /** gives the appearance the balls bounced off us */
                            bzv_BallArray.get(x).posFloor = (SCR_BG_FLOOR - bzv_Actor.H);

                            bzv_Actor.Health -= bzv_BallArray.get(x).Damage;

                            bzObj_Vibrator.vibrate(50);
                            
                            /** reduce damage to 0 after we hit the actor */
                            bzv_BallArray.get(x).Damage = 0;

                            bzv_HealthMeter.setValue(bzv_Actor.Health);

                            if(bzv_Actor.Health < 0)
                            {
                                /** vibrate and then end the game */
                                bzObj_Vibrator.vibrate(300);
                                APP_GAME_STATE = APP_STATE_DEAD;
                            }
                        }
                        
                        bzv_BallArray.get(x).Update();
                        bzv_BallArray.get(x).posFloor = (SCR_BG_FLOOR);

                    }
                    
                    bzv_Actor.Update();
                    
                    RenderLoop();
                }
                
                return null;
            }
            
            protected void onPostExecute()
            {
                return;
            }
        }

        
        public void RenderLoop()
        {
            if (!bzv_bHaveSurface)
            {
                /**
                 * this prevents a force close created when trying to render
                 * without a SurfaceHolder object.
                 */
                
                return;
            }
            
            /** lock the canvas for drawing */
            bzv_Canvas = bzv_Holder.lockCanvas();    

            bzv_Canvas.drawColor(Color.BLACK);
            
            switch (APP_GAME_STATE)
            {
                case APP_STATE_PLAY:
                {
                    /**
                     * It's much faster to draw a black rectangle than to use drawColor()
                     * With drawColor() we lose ~10 frames/second.  I don't really understand why
                     */

                    //bzv_Canvas.drawBitmap(bzv_bImagePlay, 0, bzv_BackgroundX, null);

                    for (int x = 0; (x < bzv_BallArray.size()); x++)
                    {
                        bzv_BallArray.get(x).RenderBall(bzv_Canvas);
                    }
                    
                    bzv_Actor.RenderActor(bzv_Canvas);

                    bzv_HealthMeter.Render(bzv_Canvas);
                    bzv_lblScore.Render(bzv_Canvas);

                    break;
                }
                
                /** PAUSED */
                case APP_STATE_PAUSE:
                {
                    
                    //bzv_Canvas.drawBitmap(bzv_bImagePlay, 0, bzv_BackgroundX, null);
                    bzv_Canvas.drawBitmap(bzv_bImagePause, ((SCR_W / 2) - (bzv_bImagePause.getWidth() / 2)), ((SCR_H / 2) - (bzv_bImagePause.getHeight() / 2)), null);
                    
                    bzv_lblScore.Render(bzv_Canvas);
                    
                    break;
                }
                
                /** DEAD */
                case APP_STATE_DEAD:
                {
                    //bzv_Canvas.drawBitmap(bzv_bImagePlay, 0, bzv_BackgroundX, null);
                    bzv_Canvas.drawBitmap(bzv_bImageDead, ((SCR_W / 2) - (bzv_bImageDead.getWidth() / 2)), ((SCR_H / 2) - (bzv_bImageDead.getHeight() / 2)), null);
                    
                    bzv_lblDead.Txt = String.format("%s", APP_SCORE);
                    bzv_lblDead.Render(bzv_Canvas);
                    
                    break;
                }
            }

            /** post the image to the Canvas */
            bzv_Holder.unlockCanvasAndPost(bzv_Canvas);                    
        }
        
        public void surfaceChanged(SurfaceHolder h, int format, int width, int height)
        {
            // TODO Auto-generated method stub
            
        }

        public void surfaceCreated(SurfaceHolder h)
        {
            bzv_bHaveSurface = true;
            RenderLoop();
        }

        public void surfaceDestroyed(SurfaceHolder h)
        {
            bzv_bHaveSurface = false;
        }
        
    }    

    /** progress bar class */
    private class C_ProgressBar
    {
        public float    X = 0;
        public float    Y = 0;
        public float    H = 20;
        public float    W = 100;
        
        private int     maxValue = 100;
        private int     curValue = 100;
        
        public Paint    FramePaint = new Paint();
        public Paint    FillPaint = new Paint();
        
        public C_ProgressBar()
        {
            FramePaint.setColor(Color.GRAY);
            FramePaint.setStyle(Paint.Style.STROKE);
            FramePaint.setStrokeWidth(2);

            FillPaint.setColor(Color.GREEN);
            FillPaint.setStyle(Paint.Style.FILL);
        }

        public void setValue(int x)
        {
            if (x <= 0)
            {
                x = 0;
            }

            curValue = x;
        }
        
        public int getValue()
        {
            return curValue;
        }
        
        public void Render(Canvas c)
        {
            c.drawRect(X, Y, (X + W), (Y + H), FramePaint);
            c.drawRect((X + 2), (Y + 2), ((X + W) - (maxValue - curValue) - 2), ((Y + H) - 2), FillPaint);
        }
    }

    /** ball class */
    private class C_Ball
    {
    
        public int      Damage;
        public float    X;
        public float    Y;
        
        public float    Diameter;
        
        public float    SpeedX;
        public float    SpeedY;
        
        public float    Gravity;      /** 0.150000 - 0.800000 */
    
        public float    BounceDecay;
    
        private float   posStart;
        private float   posFloor;
        
        private Paint    BallPaint = new Paint();
        
        public C_Ball()
        {
            RandomizeBall();    
        }
        
        public void RandomizeBall()
        {
            float   G;
            float   D;
            
            //Random rGen = new Random();

            G = (float)(getRandomInt(100000, 300000) / 1.0e6);  // 0.10 - 0.40
            D = (float)((getRandomInt(1, 3) * 10));
            
            if( getRandomInt(1, 25) == 13 )
            {
                /** 1:25 balls are huge (randomly) */
                D = (float)40;
            }
            
            X = 0;
            Y = (float)(getRandomInt(5, 65));
            posStart = Y;
            posFloor = (SCR_BG_FLOOR - (D / 2));
            Gravity = G;
            SpeedY = 0;
            SpeedX = (float)(getRandomInt(2, 4));
            BounceDecay = (float)(0.96);
            Diameter = D;
            Damage = (int)(D);
            
            //BallPaint.setColor(Color.BLACK);
            BallPaint.setColor(Color.WHITE);
            
            BallPaint.setStyle(Paint.Style.STROKE);
            BallPaint.setStrokeWidth(2);
            BallPaint.setAntiAlias(true);
        }
        
        public void Update()
        {
            if ((Y + Diameter) > posFloor)
            {
                Y = (posFloor - Diameter);
                SpeedY = -(SpeedY * BounceDecay);
            }
            
            SpeedY = SpeedY + Gravity;
    
            Y = Y + SpeedY;
            X = X + SpeedX;
        }
        
        public void RenderBall(Canvas c)
        {
            c.drawCircle(this.X, this.Y, this.Diameter, this.BallPaint);
        }
    
        public boolean hazCollision(C_Actor A)
        {
            /** hit zone is 75% of the actors size */

            float X1;
            float Y1;
            float X2;
            float Y2;
            
            X1 = A.X + (A.W / 2);
            Y1 = A.Y + ((A.H / 2) + SCR_BG_FLOOR);
            
            X2 = this.X;
            Y2 = this.Y + SCR_BG_FLOOR;
            
            if( getDistance(X1, Y1, X2, Y2) <= ((this.Diameter + A.H) * 0.75) )
            {
                return true;
            } else {
                return false;
            }
        }
    }

    /** actor class */
    private class C_Actor
    {
        
        public float        X, Y;
        public float        H, W;
        
        public float        Diameter;
        
        public int          Health;
        
        public Bitmap       ImageFr;
        public Bitmap       ImageRt;
        public Bitmap       ImageLt;
        
        public boolean      ACTFAST = false;
    
        public boolean      MOVLEFT = false; 
        public boolean      MOVRITE = false;
        
        public float        posChange = (float)0;
        
        public int          actorSensitivity = 3;
        
        /** construct me! */
        public C_Actor()
        {
            ImageFr = BitmapFactory.decodeResource(getResources(), R.drawable.front);
            ImageRt = BitmapFactory.decodeResource(getResources(), R.drawable.right);
            ImageLt = BitmapFactory.decodeResource(getResources(), R.drawable.left);
            
            /** these are switched because we use landscape mode exclusively */

            H = ImageFr.getWidth();
            W = ImageFr.getHeight();
            
            Health = 100;   
            Diameter = (H);

        }
        
        public void Update()
        {
            
            if ( (posChange <= 2) && (posChange >= -2) )
            {
                posChange = 0;
                
                //stop
                MOVLEFT = false;
                MOVRITE = false;
            } else if ((posChange <= -2)) {
                X += -(posChange / actorSensitivity);

                //move right, slow
                MOVLEFT = false;
                MOVRITE = true;
            } else if ((posChange >= 2)) {
                X += -(posChange / actorSensitivity);
                
                //move left, slow
                MOVLEFT = true;
                MOVRITE = false;
            }
            
            //keep the actor on the screen
            if(( X >= (SCR_W - W)))
            {
                X = (SCR_W - W);
            } else if(X < W) {
                //this prevents a bug where you cannot get hit in the left corner
                X = W;
            }
        }
        
        public void RenderActor(Canvas c)
        {
            if(this.MOVRITE)
            {
                c.drawBitmap(this.ImageRt, this.X, this.Y, null);
            } else if (this.MOVLEFT) {
                c.drawBitmap(this.ImageLt, this.X, this.Y, null);
            } else {
                c.drawBitmap(this.ImageFr, this.X, this.Y, null);
            }
        }
    }

    /** label class */
    private class C_Label
    {
        public int              X = 0;
        public int              Y = 0;

        public String           Txt = "";

        public Paint            PaintStyle = new Paint();

        public Bitmap           BitmapImage;
        
        //construct me
        public C_Label()
        {
            PaintStyle.setColor(Color.RED);
            PaintStyle.setAntiAlias(true);
            PaintStyle.setTextSize(15);
            PaintStyle.setTextAlign(Paint.Align.LEFT);
        }
        
        public void Render(Canvas c)
        {
            if((Txt != "") && (c != null))
            {
                c.drawText(this.Txt, this.X, this.Y, this.PaintStyle);
            }
        }
    }
    
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        
        Debug.startMethodTracing("/sdcard/ballz.trace");

        bzObj_SensorMgr = (SensorManager) getSystemService(SENSOR_SERVICE);
        bzObj_Sensor = bzObj_SensorMgr.getDefaultSensor(Sensor.TYPE_ORIENTATION);
        
        bzObj_bHaveSensor = bzObj_SensorMgr.registerListener((SensorEventListener) this, bzObj_Sensor, SensorManager.SENSOR_DELAY_UI );

        bzObj_Vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        
        bzObj_PowerMgr = (PowerManager)getSystemService(Context.POWER_SERVICE);
        bzObj_PowerMgr_WakeLock = bzObj_PowerMgr.newWakeLock(PowerManager.FULL_WAKE_LOCK, "Ballz");

        bzObj_PowerMgr_WakeLock.acquire();
        
        //bind this Handler for to the implied Looper interface for the GUI
        //objScoreSamuraiHandler = new Handler();
        
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN); 

        bzObj_View = new BallzView(this);
        
        APP_GAME_STATE = APP_STATE_PAUSE;
        
        bzObj_View.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.FILL_PARENT));
        
        setContentView((View) bzObj_View);
        
        bzObj_View.RenderLoop();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);
    }

    
    protected void onStart()
    {
        super.onStart();
    }

    protected void onRestart()
    {
        super.onRestart();
    }

    protected void onResume()
    {
        super.onResume();
        
        bzObj_View.RenderLoop();
    }

    protected void onPause()
    {
        super.onPause();
        
        if((APP_GAME_STATE == APP_STATE_PLAY))
        {
            APP_GAME_STATE = APP_STATE_PAUSE;
        }

        bzObj_View.RenderLoop();
    }

    protected void onStop()
    {
        super.onStop();
    }

    protected void onDestroy()
    {
        super.onDestroy();

        Debug.stopMethodTracing();
    }
    
    public boolean onTouchEvent(MotionEvent event)
    {
        switch (event.getAction())
        {
            case MotionEvent.ACTION_DOWN:
            {
                switch (APP_GAME_STATE)
                {
                    case APP_STATE_PLAY:
                    {
                        APP_GAME_STATE = APP_STATE_PAUSE;
                        break;
                    }
                    
                    case APP_STATE_PAUSE:
                    {
                        APP_GAME_STATE = APP_STATE_PLAY;
                        bzObj_View.StartMe();
                        break;
                    }

                    case APP_STATE_DEAD:
                    {
                        bzObj_View.ResetPlay();
                        break;
                    }
                }
                
                bzObj_View.RenderLoop();
                return true;
            }
        }
        
        bzObj_View.RenderLoop();
        return false;
    }
    
    public boolean onKeyDown(int keyCode, KeyEvent event)
    {
        switch (keyCode)
        {
            case KeyEvent.KEYCODE_MENU:
            {
                switch (APP_GAME_STATE)
                {
                    case APP_STATE_DEAD:
                    {
                        ScoreSamuraiSubmit();
                        break;
                    }
                    case APP_STATE_PAUSE:
                    {
                        ScoreSamuraiList();
                        break;
                    }
                }
            }
        }
        
        return true;
    }

    public void ScoreSamuraiSubmit() {
        //ScoreSamurai.enterScore((Context) this, APP_SCORE, "857bd107-b80c-43b9-8470-c7098c97f2fc", "7WA9O65GzckCKbQs", "93e98e62cfe24d84b6901563e457af7b");
    }

    public void ScoreSamuraiList() {
        //ScoreSamurai.showList((Context) this, "857bd107-b80c-43b9-8470-c7098c97f2fc", "93e98e62cfe24d84b6901563e457af7b");
    }

    public static int getRandomInt(int min, int max)
    {
        Random randomizer = new Random();
        return randomizer.nextInt((max - min) + 1) + min;
    }
    
    public static float getDistance(float x1, float y1, float x2, float y2)
    {
        return (float)Math.sqrt((Math.pow((x1 - x2), 2)) + (Math.pow((y1 - y2), 2)));
    }

    public void onAccuracyChanged(Sensor sensor, int accuracy)
    {
        // TODO Auto-generated method stub
        
    }

    public void onSensorChanged(SensorEvent event)
    {
        
        if(bzObj_View != null)
        {
            bzObj_View.bzv_Actor.posChange = event.values[1];
        }
    }
}
