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
import com.google.gson.Gson;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;
import mg.itu.prom16.annotations.Annotation_Get;
import mg.itu.prom16.annotations.Annotation_Post;
import mg.itu.prom16.annotations.Annotation_controlleur;
import mg.itu.prom16.annotations.Param;
import mg.itu.prom16.annotations.ParamField;
import mg.itu.prom16.annotations.ParamObject;
import mg.itu.prom16.annotations.Restapi;
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

    protected void processRequest(HttpServletRequest request, HttpServletResponse response) throws Exception {
        StringBuffer requestURL = request.getRequestURL();
        String[] requestUrlSplitted = requestURL.toString().split("/");
        String controllerSearched = requestUrlSplitted[requestUrlSplitted.length - 1];

        if (!error.isEmpty()) {
            displayErrorPage(response, HttpServletResponse.SC_BAD_REQUEST, error);
            return;
        }

        if (!urlMaping.containsKey(controllerSearched + request.getMethod())) {
            displayErrorPage(response, HttpServletResponse.SC_NOT_FOUND,
                    "Aucune méthode associée à ce chemin ou méthode incorrecte.");
            return;
        }

        try {
            Mapping mapping = urlMaping.get(controllerSearched + request.getMethod());
            Class<?> clazz = Class.forName(mapping.getClassName());
            Object object = clazz.getDeclaredConstructor().newInstance();
            Method method = clazz.getDeclaredMethod(mapping.getMethodeName(), HttpServletRequest.class);

            if (method == null) {
                displayErrorPage(response, HttpServletResponse.SC_NOT_FOUND, "Aucune méthode correspondante trouvée.");
                return;
            }

            injectSession(request, object);

            Object[] parameters = getMethodParameters(method, request);
            Object returnValue = method.invoke(object, parameters);

            if (method.isAnnotationPresent(Restapi.class)) {
                response.setContentType("application/json");
                Gson gson = new Gson();
                String jsonResponse = gson
                        .toJson(returnValue instanceof ModelView ? ((ModelView) returnValue).getData() : returnValue);
                PrintWriter out = response.getWriter();
                out.print(jsonResponse);
            } else {
                if (returnValue instanceof ModelView) {
                    ModelView modelView = (ModelView) returnValue;
                    for (Map.Entry<String, Object> entry : modelView.getData().entrySet()) {
                        request.setAttribute(entry.getKey(), entry.getValue());
                    }
                    RequestDispatcher dispatcher = request.getRequestDispatcher(modelView.getUrl());
                    dispatcher.forward(request, response);
                } else {
                    displayErrorPage(response, HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE,
                            "Type de données non reconnu.");
                }
            }
        } catch (Exception e) {
            displayErrorPage(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Erreur du serveur : " + e.getMessage());
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

            // Vérification si le package n'existe pas
            if (resource == null) {
                throw new Exception("Le package spécifié n'existe pas: " + packageName);
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

                                for (Method method : methods) {
                                    String url = null;
                                    String verb = "GET"; // Valeur par défaut

                                    if (method.isAnnotationPresent(Annotation_Get.class)) {
                                        url = method.getAnnotation(Annotation_Get.class).value();
                                        verb = "GET"; // GET si annoté par @GET
                                    } else if (method.isAnnotationPresent(Annotation_Post.class)) {
                                        url = method.getAnnotation(Annotation_Post.class).value();
                                        verb = "POST"; // POST si annoté par @POST
                                    }

                                    if (url != null) {
                                        if (urlMaping.containsKey(url + verb)) {
                                            throw new Exception("Conflit d'URL : l'URL " + url
                                                    + " est déjà associée à une méthode " + verb);
                                        } else {
                                            urlMaping.put(url + verb,
                                                    new Mapping(className, method.getName(), url, verb));
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
                if (parameters[i].getType() == Part.class) {
                    Part file = request.getPart(param.value());
                    upload(file);
                    parameterValues[i] = file;
                } else {
                    String paramValue = request.getParameter(param.value());
                    parameterValues[i] = convertParameter(paramValue, parameters[i].getType()); // Assuming all
                                                                                                // parameters
                }
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
                        throw new Exception("L'attribut " + fieldName + " dans la classe "
                                + parameterObject.getClass().getSimpleName() + " n'a pas d'annotation ParamField.");
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
            // Ajouter d'autres vérifications ici si nécessaire pour d'autres types
            // d'annotations
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

    // Fonction pour afficher une page d'erreur
    private void displayErrorPage(HttpServletResponse response, int errorCode, String errorMessage) throws IOException {
        response.setStatus(errorCode);
        response.setContentType("text/html");
        PrintWriter out = response.getWriter();
        out.println("<html><head><title>Error</title></head><body>");
        out.println("<h1>Erreur " + errorCode + "</h1>");
        out.println("<p>" + errorMessage + "</p>");
        out.println("</body></html>");
        out.close();
    }

    public void upload(Part filePart) throws Exception {
        // Obtenir le nom de fichier
        String fileName = filePart.getSubmittedFileName();

        // Chemin où vous souhaitez enregistrer le fichier
        String uploadPath = "D:/ITU/S5/upload/" + fileName;

        // Lire le fichier et le stocker
        try (InputStream fileContent = filePart.getInputStream();
                FileOutputStream fos = new FileOutputStream(new File(uploadPath))) {

            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = fileContent.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
            }
        } catch (Exception e) {
            throw new Exception("Erreur lors du téléchargement : " + e.getMessage());
        }
    }

}
