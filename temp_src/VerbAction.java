package mg.itu.prom16.map;

public class VerbAction {
    private String methodeName;
    private String verb;

    public VerbAction(String methodeName, String verb) {
        this.methodeName = methodeName;
        this.verb = verb;
    }

    public String getMethodeName() {
        return methodeName;
    }

    public void setMethodeName(String methodeName) {
        this.methodeName = methodeName;
    }

    public String getVerb() {
        return verb;
    }

    public void setVerb(String verb) {
        this.verb = verb;
    }

    
   
}
