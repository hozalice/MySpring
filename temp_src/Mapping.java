package mg.itu.prom16.map;

public class Mapping {
    private String className;
    private String methodeName;
    private String url;
    private String verb;

    public Mapping(String className, String methodName, String url, String verb) {
        this.className = className;
        this.methodeName = methodName;
        this.url = url;
        this.verb = verb;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getMethodeName() {
        return methodeName;
    }

    public void setMethodeName(String methodeName) {
        this.methodeName = methodeName;
    }
}
