package rita.support.behavior;

import rita.*;

public class RiInterpolater extends Interpolater
{  
  private int startTime, duration;
  private int motionType = RiText.DEFAULT_MOTION_TYPE;  
  private float startValue, currentValue, targetValue, change;
  public boolean completed;
  
  // true only after the startOffset has passed and values are being updated
  public boolean running/* = true*/;

  public RiInterpolater(float startValue, float targetValue, int startOffset, int duration) {
    this.reset(startValue, targetValue, startOffset, duration);
  }
  
  // Methods ===================================
   
  public void reset(float startVal, float targetVal, int startOffset, int duration) {

    this.completed = false;//(startVal == targetVal);
   //System.out.println("RiInterpolater.reset -> completed==true");
    this.startValue = this.currentValue = startVal;    
    this.targetValue = targetVal;    
    this.change = targetVal - startVal;
    this.duration = duration;
    this.startTime = RiTa.millis() + startOffset;
    //System.out.println("RiInterpolater(start="+startValue+" target="+targetValue+" change="+change+") +startTime="+startTime+" dur="+duration);
  }  
  
  public void reset
    (float[] startValues, float[] targetValues, int startOffset, int duration) {  
    //System.out.println("RiInterpolater("+RiTa.asList(targetValues)+","+startOffset+","+duration+")");    
    RiInterpolater2D.checkMinLen(1, startValues, targetValues); 
    this.reset(startValues[0], targetValues[0], startOffset, duration);
  }
  
  public void setStart(float[] startValue)
  {
    RiInterpolater2D.checkMinLen(1, startValue); 
    this.setStart(startValue[0]);    
  }
  
  public void setStart(float start)
  {
    this.startValue = this.currentValue = start;   
    this.change = targetValue - startValue;
    //System.out.println("RiAnimator.setStart(start="+startValue+" current="+currentValue+" target="+targetValue+")");
  }
   
  public float[] getValues() {
    return new float[] { currentValue };
  }
  
  /**
   * returns true if running, else false
   */
  public boolean update() 
  {
    int millisElapsed = RiTa.millis();
    
    // have we finished or not started yet?
    if (completed || millisElapsed < startTime)  {
      return running = false;
    }
    
    // or have we run out of time
    if (millisElapsed > (startTime + duration)) {
      stop();
      return running = false;
    }

    // ok, we are actually updating
    running = true;

    switch (motionType) 
    {        
      case RiText.LINEAR:          
        this.currentValue = linear
          (millisElapsed-startTime, startValue, change, duration);
        //System.out.println("  UPDATE("+(millisElapsed-startTime)+","+startValue+","+change+","+duration+")");
        break;
        
      case RiText.EASE_IN_OUT:
        this.currentValue = easeInOutQuad  // default to quad
          (millisElapsed-startTime, startValue, change, duration);
        break;
        
      case RiText.EASE_IN:
        this.currentValue = easeInQuad     // default to quad
          (millisElapsed-startTime, startValue, change, duration);
        break;
        
      case RiText.EASE_OUT:
        this.currentValue = easeOutQuad   // default to quad
          (millisElapsed-startTime, startValue, change, duration);
        break;     
        
      case RiText.EASE_IN_OUT_CUBIC:
        this.currentValue = easeInOutCubic
          (millisElapsed-startTime, startValue, change, duration);
        break;
        
      case RiText.EASE_IN_CUBIC:
        this.currentValue = easeInCubic 
          (millisElapsed-startTime, startValue, change, duration);
        break;
        
      case RiText.EASE_OUT_CUBIC:
        this.currentValue = easeOutCubic 
          (millisElapsed-startTime, startValue, change, duration);
        break;
        
      case RiText.EASE_IN_OUT_QUARTIC:
        this.currentValue = easeInOutQuart
          (millisElapsed-startTime, startValue, change, duration);
        break;
        
      case RiText.EASE_IN_QUARTIC:
        this.currentValue = easeInQuart
          (millisElapsed-startTime, startValue, change, duration);
        break;
        
      case RiText.EASE_OUT_QUARTIC:
        this.currentValue = easeOutQuart
          (millisElapsed-startTime, startValue, change, duration);
        break;
        
      case RiText.EASE_IN_OUT_EXPO:
        this.currentValue = easeInOutExpo
          (millisElapsed-startTime, startValue, change, duration);
        break;
        
      case RiText.EASE_IN_EXPO:
        this.currentValue = easeInExpo
          (millisElapsed-startTime, startValue, change, duration);
        break;
        
      case RiText.EASE_OUT_EXPO:
        this.currentValue = easeOutExpo
          (millisElapsed-startTime, startValue, change, duration);
        break;          
        
      case RiText.EASE_IN_OUT_SINE:
        this.currentValue = easeInOutSine
          (millisElapsed-startTime, startValue, change, duration);
        break;
        
      case RiText.EASE_IN_SINE:
        this.currentValue = easeInSine
          (millisElapsed-startTime, startValue, change, duration);
        break;
        
      case RiText.EASE_OUT_SINE:
        this.currentValue = easeOutSine
          (millisElapsed-startTime, startValue, change, duration);
        break;
        
      default:
        throw new RiTaException("Unknown MotionType: "+motionType);
    }

    return running; 
  }
  
  public boolean isCompleted()
  {
    return completed;
  }
  
  // ------------------------------------------------------------

  public float getValue()
  {
    return currentValue;
  }

  public float getStartValue() {
    return startValue;
  }

  public float getTargetValue() {
    return targetValue;
  }
  
  public float[] getTarget() {
    return new float[] {targetValue};
  }

  public void stop()
  {
    this.running = false;
    this.completed = true;
    //System.out.println("RiInterpolater.stop() -> completed==true");
  }
  
  public void finish()
  {
    this.currentValue = targetValue; 
    stop();
  }  

  public void setMotionType(int motionType)
  {
    this.motionType = motionType;    
  }
  
  public int getMotionType()
  {
    return this.motionType;    
  }

  public static void main(String[] args) throws InterruptedException
  {
    RiInterpolater e = new RiInterpolater(100, 0, 500, 2000);
    long startTime = System.currentTimeMillis();
    for (int i = 0; i < 2; i++)
    {         
      boolean completed = false;
      while (!completed) {    
        Thread.sleep(10);
        completed = e.update();
        if (i>0) System.out.println("  "+RiTa.asList(e.getValues()) + " running="+e.running);
      }   
      System.out.println("DONE: "+RiTa.asList(e.getValues())+" "+RiTa.millis(startTime));
      if (i==0) {
        Thread.sleep(200);
        e.reset(0, 100, 300, 2000);
      }
    }
    // total should be ~5000
    System.out.println("TOTAL: "+(System.currentTimeMillis()-startTime));
  }

  public boolean isRunning()
  {
    return running;
  }
 
}// end