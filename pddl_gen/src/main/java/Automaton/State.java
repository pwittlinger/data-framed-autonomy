package Automaton;

public class State {
  public String name;
  public boolean isInitial;
  public boolean isGoal;
  public boolean isFailure;
  public boolean isSpecialExistenceViolation;

  public State(String name) {
    this.name = name;
  }

    public State(State s) {
	  this.name = s.name;
	  
	  if (s.isFailure) {
		  this.isFailure = true;
	  }
	  if (s.isGoal) {
		  this.isGoal = true;
	  }
	  if (s.isInitial) {
		  this.isInitial = true;
	  }

    if (s.isSpecialExistenceViolation) {
      this.isSpecialExistenceViolation = true;
    }
  }

  public State initial() {
    this.isInitial = true;
    return this;
  }
  public State goal() {
    this.isGoal = true;
    return this;
  }
  public State failure() {
    this.isFailure = true;
    return this;
  }

  public State specialExistence() {
    this.isSpecialExistenceViolation = true;
    return this;
  }

}
