package mg.itu.prom16.spring;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
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

import jakarta.servlet.http.Part;

import com.google.gson.Gson;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.http.HttpSession;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import mg.itu.prom16.annotations.Annotation_Get;
import mg.itu.prom16.annotations.Annotation_Post;
import mg.itu.prom16.annotations.Annotation_controlleur;
import mg.itu.prom16.annotations.DateType;
import mg.itu.prom16.annotations.DoubleType;
import mg.itu.prom16.annotations.InjectSession;
import mg.itu.prom16.annotations.IntType;
import mg.itu.prom16.annotations.NotNull;
import mg.itu.prom16.annotations.Param;
import mg.itu.prom16.annotations.ParamField;
import mg.itu.prom16.annotations.ParamObject;
import mg.itu.prom16.annotations.RestApi;
import mg.itu.prom16.annotations.StringType;
import mg.itu.prom16.annotations.Url;
import mg.itu.prom16.session.CustomSession;
import mg.itu.prom16.map.ModelView;
import mg.itu.prom16.map.Mapping;
import mg.itu.prom16.map.VerbAction;

@MultipartConfig
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
        int errorCode = 0;
        String errorMessage = null;
        String errorDetails = null;

        response.setContentType("text/html");

        // Erreur de requete invalide
        if (!error.isEmpty()) {
            errorCode = 400;
            errorMessage = "Requete invalide";
            errorDetails = "La requete est mal formee ou contient des parametres non valides.";
            displayErrorPage(out, errorCode, errorMessage, errorDetails);
            return;
        }

        // Contrôleur non trouve
        if (!urlMaping.containsKey(controllerSearched)) {
            errorCode = 404;
            errorMessage = "Ressource introuvable";
            errorDetails = "Le chemin specifie ne correspond a aucune ressource disponible.";
            displayErrorPage(out, errorCode, errorMessage, errorDetails);
            return;
        }

        try {
            Mapping mapping = urlMaping.get(controllerSearched);
            Class<?> clazz = Class.forName(mapping.getClassName());
            Object object = clazz.getDeclaredConstructor().newInstance();
            Method method = null;

            // Methode HTTP non autorisee
            if (!mapping.isVerbPresent(request.getMethod())) {
                errorCode = 405;
                errorMessage = "Methode non autorisee";
                errorDetails = "La methode HTTP " + request.getMethod() + " n'est pas permise pour cette ressource.";
                displayErrorPage(out, errorCode, errorMessage, errorDetails);
                return;
            }

            // Verification de l'existence de la methode correspondante
            for (Method m : clazz.getDeclaredMethods()) {
                for (VerbAction action : mapping.getVerbActions()) {
                    if (m.getName().equals(action.getMethodeName())
                            && action.getVerb().equalsIgnoreCase(request.getMethod())) {
                        method = m;
                        break;
                    }
                }
                if (method != null) {
                    break;
                }
            }

            if (method == null) {
                errorCode = 404;
                errorMessage = "Methode introuvable";
                errorDetails = "Aucune methode appropriee n'a ete trouvee pour traiter la requete.";
                displayErrorPage(out, errorCode, errorMessage, errorDetails);
                return;
            }

            try {
                // Execution de la methode trouvee
                Object[] parameters = getMethodParameters(method, request);
                Object returnValue = method.invoke(object, parameters);

                // Gerer la reponse selon le type de retour de la methode
                if (method.isAnnotationPresent(RestApi.class)) {
                    response.setContentType("application/json");
                    Gson gson = new Gson();
                    out.println(gson.toJson(returnValue));
                } else {
                    if (returnValue instanceof ModelView) {
                        ModelView modelView = (ModelView) returnValue;
                        for (Map.Entry<String, Object> entry : modelView.getData().entrySet()) {
                            request.setAttribute(entry.getKey(), entry.getValue());
                        }
                        RequestDispatcher dispatcher = request.getRequestDispatcher(modelView.getUrl());
                        dispatcher.forward(request, response);
                    } else {
                        out.println("La methode a renvoye : " + returnValue);
                    }
                }
            } catch (Exception e) {
                // Récupérer les données saisies et les erreurs
                Map<String, String> formData = new HashMap<>();
                Map<String, String> fieldErrors = new HashMap<>();

                // Parcourir les paramètres de la méthode pour récupérer les données
                for (Parameter param : method.getParameters()) {
                    if (param.isAnnotationPresent(ParamObject.class)) {
                        Class<?> paramType = param.getType();
                        for (Field field : paramType.getDeclaredFields()) {
                            ParamField paramField = field.getAnnotation(ParamField.class);
                            if (paramField != null) {
                                String paramName = paramField.value();
                                String paramValue = request.getParameter(paramName);
                                if (paramValue != null) {
                                    formData.put(paramName, paramValue);
                                }
                            }
                        }
                    } else if (param.isAnnotationPresent(Param.class)) {
                        Param paramAnnotation = param.getAnnotation(Param.class);
                        String paramName = paramAnnotation.value();
                        String paramValue = request.getParameter(paramName);
                        if (paramValue != null) {
                            formData.put(paramName, paramValue);
                        }
                    }
                }

                // Analyser le message d'erreur pour identifier le champ concerné
                String errorMsg = e.getMessage();
                if (errorMsg != null && errorMsg.contains("Le champ")) {
                    // Extraire le nom du champ depuis le message d'erreur
                    String fieldName = errorMsg.substring(errorMsg.indexOf("Le champ") + 9,
                            errorMsg.indexOf("doit") - 1);
                    fieldErrors.put(fieldName, errorMsg);
                } else if (errorMsg != null && errorMsg.contains("Etu002748")) {
                    // Erreur d'annotation ParamField manquante
                    fieldErrors.put("general", "Erreur de configuration: annotation ParamField manquante");
                } else {
                    // Erreur générique
                    fieldErrors.put("general", "Une erreur de validation s'est produite: " + errorMsg);
                }

                // Rediriger vers le formulaire avec les données et les erreurs
                request.setAttribute("formData", formData);
                request.setAttribute("fieldErrors", fieldErrors);

                // Rediriger vers le formulaire
                RequestDispatcher dispatcher = request.getRequestDispatcher("/form.jsp");
                dispatcher.forward(request, response);
                return;
            }

        } catch (Exception e) {
            errorCode = 500;
            errorMessage = "Erreur interne du serveur";
            errorDetails = "Une erreur inattendue s'est produite lors du traitement de votre requete : "
                    + e.getMessage();
            displayErrorPage(out, errorCode, errorMessage, errorDetails);
        }
    }

    private void displayErrorPage(PrintWriter out, int errorCode, String errorMessage, String errorDetails) {
        out.println("<!DOCTYPE html>");
        out.println("<html lang='fr'>");
        out.println("<head>");
        out.println("<meta charset='UTF-8'>");
        out.println("<title>Erreur " + errorCode + "</title>");
        out.println("<style>");
        out.println("body { font-family: Arial, sans-serif; color: #333; background-color: #f4f4f4; }");
        out.println(
                ".container { max-width: 600px; margin: auto; padding: 20px; background-color: #fff; border: 1px solid #ddd; border-radius: 4px; }");
        out.println("h1 { color: #e74c3c; }");
        out.println("p { line-height: 1.5; }");
        out.println("a { color: #3498db; text-decoration: none; }");
        out.println("a:hover { text-decoration: underline; }");
        out.println("</style>");
        out.println("</head>");
        out.println("<body>");
        out.println("<div class='container'>");
        out.println("<h1>" + errorMessage + "</h1>");
        out.println("<p><strong>Code d'erreur :</strong> " + errorCode + "</p>");
        out.println("<p>" + errorDetails + "</p>");
        out.println("</div>");
        out.println("</body>");
        out.println("</html>");
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
                                Method[] methods = clazz.getDeclaredMethods();
                                for (Method method : methods) {
                                    if (method.isAnnotationPresent(Url.class)) {
                                        Url urlAnnotation = method.getAnnotation(Url.class);
                                        String url = urlAnnotation.value();
                                        String verb = "GET";
                                        if (method.isAnnotationPresent(Annotation_Get.class)) {
                                            verb = "GET";
                                        } else if (method.isAnnotationPresent(Annotation_Post.class)) {
                                            verb = "POST";
                                        }
                                        VerbAction verbAction = new VerbAction(method.getName(), verb);
                                        Mapping map = new Mapping(className);
                                        if (urlMaping.containsKey(url)) {
                                            Mapping existingMap = urlMaping.get(url);
                                            if (existingMap.isVerbAction(verbAction)) {
                                                throw new Exception("Duplicate URL: " + url);
                                            } else {
                                                existingMap.setVerbActions(verbAction);
                                            }
                                        } else {
                                            map.setVerbActions(verbAction);
                                            urlMaping.put(url, map);
                                        }

                                    } else {
                                        throw new Exception(
                                                "il faut avoir une annotation url dans le controlleur  " + className);
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

    private void validateField(Field field, String paramValue) throws Exception {
        // Vérifier si le champ est annoté avec @NotNull
        if (field.isAnnotationPresent(NotNull.class) && (paramValue == null || paramValue.trim().isEmpty())) {
            throw new Exception("Le champ " + field.getName() + " ne doit pas être nul.");
        }

        // Si la valeur est null ou vide, ne pas faire de validation de type
        if (paramValue == null || paramValue.trim().isEmpty()) {
            return;
        }

        // Vérifier le type Double
        if (field.isAnnotationPresent(DoubleType.class)) {
            try {
                Double.parseDouble(paramValue);
            } catch (NumberFormatException e) {
                throw new Exception("Le champ " + field.getName() + " doit être de type double.");
            }
        }

        // Vérifier le type Int
        if (field.isAnnotationPresent(IntType.class)) {
            try {
                Integer.parseInt(paramValue);
            } catch (NumberFormatException e) {
                throw new Exception("Le champ " + field.getName() + " doit être de type int.");
            }
        }

        // Vérifier le type String
        if (field.isAnnotationPresent(StringType.class)) {
            if (!(paramValue instanceof String)) {
                throw new Exception("Le champ " + field.getName() + " doit être de type String.");
            }
        }

        // Vérifier le type Date
        if (field.isAnnotationPresent(DateType.class)) {
            try {
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");
                sdf.parse(paramValue);
            } catch (Exception e) {
                throw new Exception("Le champ " + field.getName() + " doit être une date valide (format: yyyy-MM-dd).");
            }
        }
    }

    public static Object convertParameter(String value, Class<?> type) {
        System.out.println("=== Conversion de paramètre ===");
        System.out.println("Valeur reçue: " + value);
        System.out.println("Type cible: " + type);

        if (value == null || value.trim().isEmpty()) {
            System.out.println("Valeur null ou vide, retourne null");
            return null;
        }

        try {
            if (type == String.class) {
                return value;
            } else if (type == Integer.class || type == int.class) {
                return Integer.parseInt(value);
            } else if (type == Double.class || type == double.class) {
                return Double.parseDouble(value);
            } else if (type == java.sql.Date.class) {
                try {
                    System.out.println("Tentative de conversion en Date SQL: " + value);
                    // Convertir d'abord en java.util.Date
                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");
                    java.util.Date utilDate = sdf.parse(value);
                    // Puis en java.sql.Date
                    java.sql.Date sqlDate = new java.sql.Date(utilDate.getTime());
                    System.out.println("Date convertie avec succès: " + sqlDate);
                    return sqlDate;
                } catch (Exception e) {
                    System.out.println("Erreur lors de la conversion de la date: " + e.getMessage());
                    e.printStackTrace();
                    return null;
                }
            }
        } catch (Exception e) {
            System.out.println("Erreur de conversion: " + e.getMessage());
            e.printStackTrace();
            return null;
        }

        System.out.println("Type non géré: " + type);
        return null;
    }

    private Object[] getMethodParameters(Method method, HttpServletRequest request) throws Exception {
        Parameter[] parameters = method.getParameters();
        Object[] parameterValues = new Object[parameters.length];

        for (int i = 0; i < parameters.length; i++) {

            if (parameters[i].isAnnotationPresent(Param.class)) {
                Param param = parameters[i].getAnnotation(Param.class);
                String paramValue = request.getParameter(param.value());
                if (parameters[i].getType().equals(Part.class)) {
                    Part filePart = request.getPart(param.value());
                    String fileName = filePart.getSubmittedFileName();
                    String filePath = "D:/ITU/S4/Mr_Naina/files_Upload/" + fileName;

                    // Enregistrer le fichier sur le serveur
                    try (InputStream fileContent = filePart.getInputStream();
                            FileOutputStream fos = new FileOutputStream(new File(filePath))) {

                        byte[] buffer = new byte[1024];
                        int bytesRead;

                        while ((bytesRead = fileContent.read(buffer)) != -1) {
                            fos.write(buffer, 0, bytesRead);
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    parameterValues[i] = filePart;
                } else {
                    parameterValues[i] = convertParameter(paramValue, parameters[i].getType()); // Assuming all
                                                                                                // parameters are
                                                                                                // strings for
                                                                                                // simplicity
                }
            }
            // Verifie si le parametre est annote avec @RequestObject
            else if (parameters[i].isAnnotationPresent(ParamObject.class)) {
                Class<?> parameterType = parameters[i].getType(); // Recupere le type du parametre (le type de l'objet a
                                                                  // creer)
                Object parameterObject = parameterType.getDeclaredConstructor().newInstance(); // Cree une nouvelle
                                                                                               // instance de cet objet

                // Parcourt tous les champs (fields) de l'objet
                for (Field field : parameterType.getDeclaredFields()) {
                    ParamField param = field.getAnnotation(ParamField.class);
                    String fieldName = field.getName(); // Recupere le nom du champ
                    if (param == null) {
                        throw new Exception("Etu002748 ,l'attribut " + fieldName + " dans le classe "
                                + parameterObject.getClass().getSimpleName() + " n'a pas d'annotation ParamField ");
                    }
                    String paramName = param.value();
                    String paramValue = request.getParameter(paramName); // Recupere la valeur du parametre de la
                                                                         // requete

                    // Verifie si la valeur du parametre n'est pas null (si elle est trouvee dans la
                    // requete)
                    if (paramValue != null) {
                        validateField(field, paramValue);
                        Object convertedValue = convertParameter(paramValue, field.getType()); // Convertit la valeur de
                                                                                               // la requete en type de
                                                                                               // champ requis

                        // Construit le nom du setter
                        String setterName = "set" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
                        Method setter = parameterType.getMethod(setterName, field.getType()); // Recupere la methode
                                                                                              // setter correspondante
                        setter.invoke(parameterObject, convertedValue); // Appelle le setter pour definir la valeur
                                                                        // convertie dans le champ de l'objet
                    }
                }
                parameterValues[i] = parameterObject; // Stocke l'objet cree dans le tableau des arguments
            } else if (parameters[i].isAnnotationPresent(InjectSession.class)) {
                parameterValues[i] = new CustomSession(request.getSession());
            } else {
                // Paramètre sans annotation - passer null
                parameterValues[i] = null;
            }
        }

        return parameterValues;
    }

    private void injectSessionIfNeeded(Object controllerInstance, HttpSession session) throws IllegalAccessException {
        Field[] fields = controllerInstance.getClass().getDeclaredFields();
        for (Field field : fields) {
            if (field.isAnnotationPresent(InjectSession.class)) {
                boolean accessible = field.isAccessible();
                field.setAccessible(true);
                field.set(controllerInstance, new CustomSession(session));
                field.setAccessible(accessible);
            }
        }
    }

}
