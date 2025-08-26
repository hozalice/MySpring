package mg.itu.prom16.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation pour contrôler l'accès aux méthodes par rôle utilisateur
 * Usage: @Auth("Admin") pour restreindre l'accès aux administrateurs uniquement
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Auth {
    /**
     * Le rôle requis pour accéder à la méthode
     * @return le nom du rôle (ex: "Admin", "User", etc.)
     */
    String value();
}
