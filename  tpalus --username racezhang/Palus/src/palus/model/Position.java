package palus.model;

import java.io.Serializable;

import palus.PalusUtil;

/**
 * This simple class represents the position where an object could
 * appear in a method. for example:
 * Constructs:     ret-type    this  (param1, param2, .... param n)
 * Positions:        -1         0        1       2    ....   n
 * */
public final class Position implements Serializable {
	private int i = Integer.MAX_VALUE;
	
	//private constructor for purpose
	private Position(int i) {
		this.i = i;
	}
	
	//some static factory methods
	public static Position getThisPosition() {
		return new Position(0);
	}
	
	public static Position getRetPosition() {
		return new Position(-1);
	}
	
	public static Position getParaPosition(int i) {
		PalusUtil.checkTrue(i > 0);
		return new Position(i);
	}
	
	public static Position getMockPosition() {
		return new Position(Integer.MAX_VALUE);
	}
	
	//check the position property
	public boolean isThisPosition() {
		return i == 0;
	}
	
	public boolean isRetPosition() {
		return i == -1;
	}
	
	public boolean isParamPosition() {
		return i > 0;
	}
	//return 1 - param.length
	public int getParamPosition() {
		PalusUtil.checkTrue(this.isParamPosition());
		return this.i;
	}
	
	//return its int value
	public int toIntValue() {
	  PalusUtil.checkTrue(i != Integer.MAX_VALUE);
	  return this.i;
	}
	
	@Override
	public String toString() {
	  return "position: " + this.i;
	}
	
	@Override
	public int hashCode() {
	  return this.i * 107;
	}
	
	@Override
	public boolean equals(Object o) {
	  if(!(o instanceof Position)) {
	    return false;
	  } else {
	    Position p = (Position)o;
	    return p.toIntValue() == this.i;
	  }
	}
}