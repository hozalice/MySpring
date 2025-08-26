package mg.itu.prom16.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation personnalisée pour marquer un champ comme étant un type de date.
 */
@Target({ ElementType.FIELD, ElementType.PARAMETER }) // Cette annotation s'applique aux champs et aux paramètres
@Retention(RetentionPolicy.RUNTIME) // Disponible à l'exécution
public @interface DateType {
}
