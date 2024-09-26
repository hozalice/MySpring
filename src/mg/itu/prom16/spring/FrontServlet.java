package mg.itu.prom16.spring;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.net.URISyntaxException;
import java.lang.reflect.Field;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import mg.itu.prom16.annotations.Annotation_Get;
import mg.itu.prom16.annotations.Annotation_Post;
import mg.itu.prom16.annotations.Annotation_controlleur;
import mg.itu.prom16.annotations.Param;
import mg.itu.prom16.annotations.ParamField;
import mg.itu.prom16.annotations.ParamObject;
import mg.itu.prom16.map.ModelView;
import mg.itu.prom16.session.MySession;
import mg.itu.prom16.map.Mapping;

public class FrontServlet extends HttpServlet {
    private String packageName; // Variable pour stocker le nom du package
    private static List<String> controllerNames = new ArrayList<>();
    private HashMap<String, Mapping> urlMaping = new HashMap<>();
    String error = "";

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        packageName = config.getInitParameter("packageControllerName"); // Recuperation du nom du package
        try {
            // Verification si le packageControllerName n'existe pas
            if (packageName == null || packageName.isEmpty()) {
                throw new Exception("Le nom du package du contrôleur n'est pas specifie.");
            }
            // Scanne les contrôleurs dans le package
            scanControllers(packageName);
        } catch (Exception e) {
            error = e.getMessage();
        }
    }

    protected void processRequest(HttpServletRequest request, HttpServletResponse response)
    throws Exception {
StringBuffer requestURL = request.getRequestURL();
String[] requestUrlSplitted = requestURL.toString().split("/");
String controllerSearched = requestUrlSplitted[requestUrlSplitted.length - 1];

PrintWriter out = response.getWriter();
response.setContentType("text/html");
if (!error.isEmpty()) {
    out.println(error);
} else if (!urlMaping.containsKey(controllerSearched)) {
    out.println("<p>Aucune methode associee à ce chemin.</p>");
} else {
    try {
        Mapping mapping = urlMaping.get(controllerSearched);
        Class<?> clazz = Class.forName(mapping.getClassName());
        Object object = clazz.getDeclaredConstructor().newInstance();
        Method method = null;

        for (Method m : clazz.getDeclaredMethods()) {
            if (m.getName().equals(mapping.getMethodeName())) {
                if (request.getMethod().equalsIgnoreCase("GET") && m.isAnnotationPresent(Annotation_Get.class)) {
                    method = m;
                    break;
                } else if (request.getMethod().equalsIgnoreCase("POST") && m.isAnnotationPresent(Annotation_Post.class)) {
                    method = m;
                    break;
                }
            }
        }

        if (method == null) {
            out.println("<p>Aucune méthode correspondante trouvée.</p>");
            return;
        }

        // Inject session into the controller if it has a MySession parameter
        injectSession(request, object);

        // Inject parameters
        Object[] parameters = getMethodParameters(method, request);
        Object returnValue = method.invoke(object, parameters);

        if (returnValue instanceof String) {
            out.println("Methode trouvee dans " + (String) returnValue);
        } else if (returnValue instanceof ModelView) {
            ModelView modelView = (ModelView) returnValue;
            for (Map.Entry<String, Object> entry : modelView.getData().entrySet()) {
                request.setAttribute(entry.getKey(), entry.getValue());
            }
            RequestDispatcher dispatcher = request.getRequestDispatcher(modelView.getUrl());
            dispatcher.forward(request, response);
        } else {
            out.println("Type de donnees non reconnu");
        }
    } catch (Exception e) {
        out.println(e.getMessage());
    } finally {
        out.close();
    }
}
}

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            processRequest(request, response);
        } catch (Exception e) {
            e.printStackTrace();
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An internal error occurred");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            processRequest(request, response);
        } catch (Exception e) {
            e.printStackTrace();
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An internal error occurred");
        }
    }

    private void scanControllers(String packageName) throws Exception {
        try {
            // Charger le package et parcourir les classes
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            String path = packageName.replace('.', '/');
            URL resource = classLoader.getResource(path);

            // Verification si le package n'existe pas
            if (resource == null) {
                throw new Exception("Le package specifie n'existe pas: " + packageName);
            }

            Path classPath = Paths.get(resource.toURI());
            Files.walk(classPath)
                    .filter(f -> f.toString().endsWith(".class"))
                    .forEach(f -> {
                        String className = packageName + "." + f.getFileName().toString().replace(".class", "");
                        try {
                            Class<?> clazz = Class.forName(className);
                            if (clazz.isAnnotationPresent(Annotation_controlleur.class)
                                    && !Modifier.isAbstract(clazz.getModifiers())) {
                                controllerNames.add(clazz.getSimpleName());
                                Method[] methods = clazz.getMethods();

                                for (Method methode : methods) {
                                    if (methode.isAnnotationPresent(Annotation_Get.class)) {
                                        Mapping map = new Mapping(className, methode.getName());
                                        String valeur = methode.getAnnotation(Annotation_Get.class).value();
                                        if (urlMaping.containsKey(valeur)) {
                                            throw new Exception("double url" + valeur);
                                        } else {
                                            urlMaping.put(valeur, map);
                                        }
                                    } else if (methode.isAnnotationPresent(Annotation_Post.class)) {
                                        Mapping map = new Mapping(className, methode.getName());
                                        String valeur = methode.getAnnotation(Annotation_Post.class).value();
                                        if (urlMaping.containsKey(valeur)) {
                                            throw new Exception("double url" + valeur);
                                        } else {
                                            urlMaping.put(valeur, map);
                                        }
                                    }
                                }
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });
        } catch (Exception e) {
            throw e;
        }
    }

  public static Object convertParameter(String value, Class<?> type) {
        if (value == null) {
            return null;
        }
        if (type == String.class) {
            return value;
        } else if (type == int.class || type == Integer.class) {
            return Integer.parseInt(value);
        } else if (type == long.class || type == Long.class) {
            return Long.parseLong(value);
        } else if (type == boolean.class || type == Boolean.class) {
            return Boolean.parseBoolean(value);
        }
        // Ajoutez d'autres conversions nécessaires ici
        return null;
    }

 private Object[] getMethodParameters(Method method, HttpServletRequest request) throws Exception {
    Parameter[] parameters = method.getParameters();
    Object[] parameterValues = new Object[parameters.length];

    for (int i = 0; i < parameters.length; i++) {
        // Si le paramètre est annoté avec @Param
        if (parameters[i].isAnnotationPresent(Param.class)) {
            Param param = parameters[i].getAnnotation(Param.class);
            String paramValue = request.getParameter(param.value());
            parameterValues[i] = convertParameter(paramValue, parameters[i].getType());
        }
        // Si le paramètre est annoté avec @ParamObject (création d'objet)
        else if (parameters[i].isAnnotationPresent(ParamObject.class)) {
            Class<?> parameterType = parameters[i].getType();
            Object parameterObject = parameterType.getDeclaredConstructor().newInstance();

            // Parcourir les champs de l'objet pour injecter les valeurs des paramètres
            for (Field field : parameterType.getDeclaredFields()) {
                ParamField param = field.getAnnotation(ParamField.class);
                String fieldName = field.getName();
                if (param == null) {
                    throw new Exception("L'attribut " + fieldName + " dans la classe " + parameterObject.getClass().getSimpleName() + " n'a pas d'annotation ParamField.");
                }
                String paramName = param.value();
                String paramValue = request.getParameter(paramName);

                if (paramValue != null) {
                    Object convertedValue = convertParameter(paramValue, field.getType());

                    // Construire et appeler le setter pour le champ de l'objet
                    String setterName = "set" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
                    Method setter = parameterType.getMethod(setterName, field.getType());
                    setter.invoke(parameterObject, convertedValue);
                }
            }
            parameterValues[i] = parameterObject;
        }
        // Si le paramètre est de type MySession, injecter l'objet session
        else if (parameters[i].getType().equals(MySession.class)) {
            parameterValues[i] = new MySession(request.getSession());
        }
        // Ajouter d'autres vérifications ici si nécessaire pour d'autres types d'annotations
        else {
            parameterValues[i] = null; // Par défaut, si aucun paramètre n'est trouvé
        }
    }

    return parameterValues;
}
public static void injectSession(HttpServletRequest req, Object controller) {
    try {
        // Récupérer la méthode setSession si elle existe dans le contrôleur
        Method setSessionMethod = controller.getClass().getMethod("setSession", MySession.class);
        
        // Si la méthode existe, l'invoquer avec une nouvelle instance de MySession
        if (setSessionMethod != null) {
            MySession mySession = new MySession(req.getSession());
            setSessionMethod.invoke(controller, mySession);
        }
    } catch (NoSuchMethodException e) {
        // La méthode setSession n'existe pas dans ce contrôleur, ignorer
    } catch (Exception e) {
        e.printStackTrace(); // Gérer les autres exceptions éventuelles
    }
}



}
