package rita;

import java.util.*;

import rita.support.RiConstants;
import rita.support.behavior.BehaviorListener;

/**
 * An abstract superclass for an extensible set of text-behaviors (found in rita.support.behaviors) 
 * including a variety of interpolation algorithms for moving, fading, scaling, color-change, etc.
 * <p>
 * Included in the rita.* package primarily to document callbacks as follows:<br>
 * <pre>
 *    public void onRiTaEvent(RiTaEvent re)
 *    {
 *      // do something with the RiText whose behavior has finished
 *      RiText parent = (RiText)re.getSource();  
 *      ...
 *      
 *      // do something with the Behavior directly
 *      RiTextBehavior rtb = (RiTextBehavior)re.getData();     
 *      ...
 *      
 *    }<pre>
 * @author dhowe
 * @invisible
 */
public class RiTextBehavior implements RiConstants
{        

  /** @invisible */
  public static List instances;
  
  protected static final float SLOP = .01f; // for crappy java scheduler 

  /** @invisible */
  public float duration = 0;  // seconds
  
  /** @invisible */
  public float startOffset = 0; // seconds
  
  /** @invisible */
  public long startTime = 0; // millis
 

  /** @invisible */
  public boolean completed;
      
  protected List listeners;
  protected RiText rt;  
  protected float pauseFor;
  protected boolean initd, running, /*paused,*/ repeating;
  protected int type;
  protected String name;
  
  private boolean reusable;
  private int id = -1;
  private float remainingAfterPauseMs;
  
  /**
   * Creates a behavior to start immediately
   */
  public RiTextBehavior(RiText riText, float duration) {
    this(riText, null, -1, duration);
  }
  
  /**
   * Creates a behavior and assigns it an Id. Note: the
   * behavior cannot start w'out first being assigned a duration.
   */
  public RiTextBehavior(RiText riText) {
    this(riText, null, -1, -1);
  }
  
  /**
   * Creates a behavior to start immediately
   */
  public RiTextBehavior(RiText riText, String timerName, float duration) {
    this(riText, timerName, -1, duration);
  }

  /**
   * Creates a behavior to start at <code>startOffsetInSec</code> 
   * seconds in the future
   */
  public RiTextBehavior(RiText riText, float startOffsetInSec, float durationInSeconds)  {
    this(riText, null, startOffsetInSec, durationInSeconds);
  }
  
  /**
   * Creates a behavior to start at <code>startOffsetInSec</code> 
   * seconds in the future
   */
  protected RiTextBehavior(RiText riText, String timerName, float startOffsetInSec, float durationInSeconds) 
  {
    initBehavior(riText, timerName, startOffsetInSec, durationInSeconds, false, RiObject.nextId());
  }
  
  /**
   * Creates a behavior to start at <code>startOffsetInSec</code> 
   * seconds in the future, that may repeat indefinately
   */
  protected RiTextBehavior(RiText riText, String timerName, float startOffsetInSec, float durationInSeconds, boolean repeating) 
  {
    initBehavior(riText, timerName, startOffsetInSec, durationInSeconds, repeating, RiObject.nextId());
  }
  
  protected void initBehavior(RiText riText, String timerName, float startOffsetInSec, float durationInSeconds, boolean repeating, int theId) 
  {
    //System.out.println("RiTextBehavior.initBehavior("+riText+","+startOffsetInSec+","+durationInSeconds+","+theId+")");
    
    if (instances == null)
      instances = new ArrayList();
    
    synchronized(RiTextBehavior.class)
    {
      if (!instances.contains(this))
        instances.add(this);
      
      //if (instances.size() % 50==0)System.out.println("[INFO] "+instances.size()+" live behaviors, "+creations +" created...");
    }
    
    this.repeating = repeating;
    this.completed = false;
    this.running = true;
    this.rt = riText;
    this.id = theId;
    
    if (id < 0) 
      id = RiObject.nextId(); 
    
    if (timerName != null) 
      setName(timerName);
    
    if (durationInSeconds < 0) return;
    
    this.duration = durationInSeconds; 
    this.startOffset = startOffsetInSec;  
    this.startTime = RiTa.millis();
    this.pauseFor = 0;
  } 
  
  // ---------------------------- methods ----------------------------
  
  /**
   * Returns the sublist of the specified behaviors that is of the specified type, 
   * where type is generally one of (MOVE, FADE_IN, FADE_OUT, FADE_TO_TEXT, SCALE_TO, etc.) 
   * @invisible
   */
  public static List selectByType(List behaviors, int type) {
    List l = new ArrayList();
    for (Iterator it = behaviors.iterator(); it.hasNext();)
    {
      RiTextBehavior rtb = (RiTextBehavior) it.next();
      if (rtb.getType()==type) l.add(rtb);
    }
    return l;
  }
  
  /**
   * Stops and deletes all the behaviors for the specified RiText that are of type 
   * FADE_IN, FADE_OUT or FADE_TO_TEXT. 
   * @invisible
   */
  public static void deleteAllFades(RiText rt) {
    List bh = rt.getBehaviors();
    if (bh == null) return;
    for (int i = 0; i < bh.size(); i++) {
      RiTextBehavior rtb = ((RiTextBehavior)bh.get(i));
      if (rtb != null && (rtb.type == FADE_IN || rtb.type == FADE_OUT
          || rtb.type == FADE_COLOR || rtb.type == FADE_TO_TEXT))
      {
        rtb.delete();
      }
    }
  }

  
  /**
   * Returns the behavior corresponding to the specified 'id'.
   */
  public static RiTextBehavior getBehaviorById(int id) {
    for (Iterator it = instances.iterator(); it.hasNext();) {
      RiTextBehavior rtb = (RiTextBehavior) it.next();
      if (rtb != null && rtb.getId() == id)
        return rtb;
    }
    return null;
  }

  
  /** Required method for all subclasses      */
  public void update() {
    
    if (completed || /*paused ||*/ duration <= 0) return;   
    
    float sofar = (RiTa.millis() - startTime)/1000f;

    if (sofar >= ((duration + pauseFor/1000f) - SLOP)) {    
      if (pauseFor>0) pauseFor=0;
      completed = true;
    }
    
    checkForCompletion();
  }
    
  public boolean isWaiting() {
    return RiTa.millis() < startTime;
  }

  /** Returns the RiText object in which this behavior is operating. */
  public RiText getParent() {// better name? getRiText()?   
    return rt;
  }
 
  /**
   * Causes the behavior to be (immediately) repeated with its initial params 
   * and the specified 'duration'<p>
   * Note: ignores startOffset and restarts immediately.
   */
  public void reset(float durationInSec) {   
    initBehavior(rt, name, 0, durationInSec, repeating, getId());
  }
       
  public void finish() {    
    completed = true;
  }

  public void stop() {
    completed = true;
  }
  
  /**
   * Sets whether the behavior should repeat (indefinately) when finished
   */
  public void setRepeating(boolean repeating) {
    this.repeating = repeating;
  }
  
  /** Checks for completion, and if so, fires the callback */
  public void checkForCompletion() {
    if (running && /*!paused && */completed) {
       fireCallback();
       if (!repeating && !reusable) {
        //System.out.println("RiTextBehavior.checkForCompletion(deleteOnComplete="+isReusable()+")");
         delete();
       }
    }
  }
  
  /** Returns whether this behavior has completed */
  public boolean isCompleted() {
    return this.completed;
  }
    
  protected int toOffsetMs(float _startTime) {
    return (int)(_startTime*1000);
  }
  
  protected int toMs(float timeInSeconds) {
    return (int)(1000*timeInSeconds);
  }
  
  /**
   * Pauses/un-pauses all existing behaviors
   * @param paused
   */
  public static synchronized void pauseAll(boolean paused) {
    for (Iterator i = instances.iterator(); i.hasNext();)
      ((RiTextBehavior) i.next()).setPaused(paused);      
  }
  
  /**
   * Calls delete() on all existing behaviors 
   */
  public static synchronized void disposeAll() {
    for (Iterator iter = instances.iterator(); iter.hasNext();) {
      RiTextBehavior rtb = ((RiTextBehavior) iter.next());
      rtb.delete();
    }
  }
  
  /**
   * Optional method for subclasses needing 
   * to clean-up/release resources<br>
   *    (Note: implementations should generally call super.delete())
   */
  public synchronized void delete() {
    //System.out.println("RiTextBehavior.delete()");
    this.running = false;
    this.stop();  
    if (rt != null && rt.behaviors != null)
      rt.behaviors.remove(this);    
    instances.remove(this);    
  }

  /** Returns the total duration for the behavior  */
  public float getDuration()
  {
    return this.duration;
  }

  /** Returns the original startOffset for the behavior  */
  public float getStartOffset()
  {
    return this.startOffset;
  }

  /** Returns the type for the behavior  */
  public int getType()
  {
    return this.type;
  }
  
  /** Returns the paused status for the behavior  */
  public boolean isRunning()
  {
    return this.running;
  }

  /** Sets the paused status for the behavior  */
  public void setPaused(boolean pausedVal)
  {
    if (pausedVal) {
      //paused = true; // pause first
      pauseFor = Float.MAX_VALUE;
      // compute how long its been running, and how much remaining
      int soFar = (int)(RiTa.millis() - startTime);
      remainingAfterPauseMs = (int)(duration*1000 - soFar);
      //pauseFor = remaining;
//System.out.println("Paused after "+soFar+"s with "+pauseFor+" remaining(ms)");
    }
    else {
//System.out.println("Unpaused at "+RiTa.millis()/1000f+"s with "+pauseFor+" left, expectint finish at"+(RiTa.millis()+pauseFor)/1000f+"s");
      reset(remainingAfterPauseMs/1000f);
    }
    //paused = pausedVal;
  } 
    
  /**
   * Pauses the behavior for 'pauseTime' seconds
   */
  public void pause(float pauseTime)
  {
/*    System.err.println("[WARN] pause(float) may not be accurate,"
    	+ " setPaused(boolean) is recommended instead");*/
    this.pauseFor = pauseTime;
  }
  
  /** Returns the paused status for the behavior  */
  public boolean isPaused()
  {
    return pauseFor>0;
    //return this.paused;
  }
  
  protected void fireCallback()
  {    
      //System.out.println("completed: "+this+" / '"+getParent().getText()+"' type="+getType());
      running = false;      

      int behaviorType = getType();
      if (rt != null && !RiText.callbacksDisabled) {   
        int eventType = BEHAVIOR_COMPLETED;
        
        // no callbacks for this (hack!)
        if (behaviorType != BOUNDING_BOX_ALPHA) {

          if (behaviorType == TIMER) eventType = TIMER_TICK;
//          
//          boolean ok = RiTa.fireEvent(rt.getPApplet(),
//            new RiTaEvent(rt, eventType, this)); 
//          
//          if (!ok) {
//            if (behaviorType == TIMER) {
//              System.err.println("\n[WARN] Possible coding error? You appear to have " +
//                "created a callback timer,\n       but not implemented the method: "  +
//                "'void onRiTaEvent(RiTaEvent rt)'");
//            }
//            RiText.callbacksDisabled = true;
//          }
        }
      }  
      
      if (behaviorType != BOUNDING_BOX_ALPHA)   // ugh, hack (tmp)
      {
        notifyListeners();  // now tell any listeners
      }
      
      if (isRepeating()) {          
        reset(duration);          
      }
/*      else {
        if (rt != null) delete();  // do we delete here?? 
      }  */        
  } 

  /** @invisible */
  private void notifyListeners()
  {
    if (listeners != null) {
      for (Iterator i = listeners.iterator(); i.hasNext();)
        ((BehaviorListener) i.next()).behaviorCompleted(this);
    }
  }
  
  /**
   * Adds a listener for events fired from this behavior,
   * e.g., completion, upon which it will behaviorCompleted(); 
   * @invisible  
   */
  public void addListener(BehaviorListener bl)
  {
    if (listeners == null) 
      this.listeners = new LinkedList();
    listeners.add(bl);    
  }
  
  /**
   * @invisible
   */
  public void setType(int type)
  {
    this.type = type;
  }

  /**
   * @invisible
   */
  public void setRunning(boolean b)
  {
    this.running = b;    
  }

  /**
   * Returns whether the behavior will repeat (indefinately) when finished
   */
  public boolean isRepeating() {
    return repeating;
  }

  /**
   * Sets whether the behavior should repeat (indefinately) when finished
   * Note: only implemented in some subclasses 
   
  public void setRepeating(boolean repeat) {
    if (repeat) System.err.println
      ("[WARN] setRepeating(true) not allowed for "+getClass());
  }*/

  /**
   * Sets a (user-assigned) name for this behavior.
   */
  public void setName(String name) {
    this.name = name;
  }
  
  /**
   * Returns the user-assigned name for this behavior;
   * Will return the class name if not set by the user.
   */
  public String getName() {
    if (name == null)
      name = RiTa.shortName(getClass());
    return name;
  }
  
  public String toString() {
    return getName()+"#"+getId();
  }

  public void setId(int id) {
    this.id = id;
  }
  
  /** Returns the unique Id for this behavior  */
  public int getId() { return id; }

  public float getValue()
  {
    System.out.println("[WARN] the getValue() method in RiTextBehavior " +
    		"should not be accessed directly, but instead overridden in subclasses");
    return Float.MIN_VALUE;
  }
  
  /** @invisible */
  public static List findByType(int type) {
    List l = new ArrayList();
    if (instances == null) return l;
    for (int i = 0; i < instances.size(); i++) {     
      RiTextBehavior rtb = (RiTextBehavior) instances.get(i);
      if (rtb != null && rtb.getType() == type) 
        l.add(rtb); 
    }
    return l;
  }
  /** @invisible */
  public static RiTextBehavior findById(int id) {
    if (instances == null) return null;
    for (int i = 0; i < instances.size(); i++) {     
      RiTextBehavior rtb = (RiTextBehavior) instances.get(i);
      if (rtb != null && rtb.getId() == id) 
        return rtb; 
    }
    return null;
  }
  /** @invisible */
  public static RiTextBehavior findByName(String name) {  
    if (instances == null) return null;
    for (int i = 0; i < instances.size(); i++) {     
      RiTextBehavior rtb = (RiTextBehavior) instances.get(i);
      if (rtb != null && rtb.getName().equals(name)) 
        return rtb; 
    }
    return null;    
  }

  /** @invisible */
  public void setReusable(boolean reusable)
  {
    this.reusable = reusable;
  }
  /** @invisible */
  public boolean isReusable()
  {
    return reusable;
  }
  
}// end
