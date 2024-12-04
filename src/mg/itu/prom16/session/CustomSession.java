package mg.itu.prom16.session;

import java.util.HashMap;

import jakarta.servlet.http.HttpSession;

public class CustomSession {
    private HttpSession session;

    public CustomSession(HttpSession session) {
        this.session = session;
    }

    public void add(String key, Object value) {
        session.setAttribute(key, value);
    }

    public Object get(String key) {
        return session.getAttribute(key);
    }

    public void delete(String key) {
        session.removeAttribute(key);
    }

    public void invalidate() {
        session.invalidate();
    }

}
