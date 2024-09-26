package mg.itu.prom16.session;

import jakarta.servlet.http.HttpSession;

public class MySession {
    private HttpSession session;

    public MySession(HttpSession session) {
        this.session = session;
    }

    // Méthode pour récupérer un objet de la session
    public Object get(String key) {
        return session.getAttribute(key);
    }

    // Méthode pour ajouter un objet à la session
    public void add(String key, Object objet) {
        session.setAttribute(key, objet);
    }

    // Méthode pour supprimer un objet de la session
    public void delete(String key) {
        session.removeAttribute(key);
    }
}

