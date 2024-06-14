package mg.itu.prom16.spring;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import mg.itu.prom16.annotations.AnnotationGet;
import mg.itu.prom16.annotations.AnnotationPost;
import mg.itu.prom16.annotations.Annotation_controlleur;
import mg.itu.prom16.annotations.Param;
import mg.itu.prom16.map.Mapping;
import mg.itu.prom16.map.ModelView;

public class FrontController extends HttpServlet {
    private String packageName; // Variable pour stocker le nom du package
    private static List<String> controllerNames = new ArrayList<>();
    HashMap <String,Mapping> urlMaping = new HashMap<>() ;
    
    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        packageName = config.getInitParameter("packageControllerName"); // Récupération du nom du package
        if (packageName== null || packageName.isEmpty()) {
            throw new ServletException("tsy misy ilay package ilay controlleur");
        }
        try {
            scanControllers(packageName);
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            try {
                throw new Exception("il y a eu une erreur sur la scan du controlleur");
            } catch (Exception e1) {
                // TODO Auto-generated catch block
                e1.printStackTrace();
            }
        }
    }

    /**
     * @param request
     * @param response
     * @throws ServletException
     * @throws IOException
     */
    @SuppressWarnings("unused")
    protected void processRequest(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        StringBuffer requestURL = request.getRequestURL();
        String[] requestUrlSplitted = requestURL.toString().split("/");
        String controllerSearched = requestUrlSplitted[requestUrlSplitted.length-1];
        
        PrintWriter out = response.getWriter();
        response.setContentType("text/html");

        if (!urlMaping.containsKey(controllerSearched)) {
            out.println("<p>"+"Aucune methode associee a cette url ou chemin."+"</p>");
        }
        else {
            try {
                Mapping mapping = urlMaping.get(controllerSearched);
                
                // out.println("<p>" + requestURL.toString() + "</p>");
                // out.println("<p>" + mapping.getClassName() + "</p>");
                // out.println("<p>" + mapping.getMethodeName() + "</p>");
        
                Class<?> class1 = Class.forName(mapping.getClassName());
                Method method = class1.getMethod(mapping.getMethodeName());
                Object declaredObject = class1.getDeclaredConstructor().newInstance();
                Object returnValue = method.invoke(declaredObject);
                Method method1 = null;

                // Find the method that matches the request type (GET or POST)
                for (Method m : class1.getDeclaredMethods()) {
                    if (m.getName().equals(mapping.getMethodeName())) {
                        if (request.getMethod().equalsIgnoreCase("GET") && m.isAnnotationPresent((Class<? extends Annotation>) AnnotationGet.class)) {
                            method1 = m;
                            break;
                        } else if (request.getMethod().equalsIgnoreCase("POST") && m.isAnnotationPresent((Class<? extends Annotation>) AnnotationPost.class)) {
                            method1 = m;
                            break;
                        }
                    }
                }

                if (method == null) {
                    out.println("<p>Aucune méthode correspondante trouvée.</p>");
                    return;
                }

                // Inject parameters
                final Object[] parameters = getMethodParameters(method, request);
                
                Object object = class1.getDeclaredConstructor().newInstance();
                Object returnValue1 = method.invoke(object, parameters);
                if (returnValue1 instanceof String) {
                    String result = (String) returnValue;
                    out.println("<p>" + result + "</p>");
                }
                else if (returnValue instanceof ModelView) {
                    ModelView modelView= (ModelView)returnValue;
                    RequestDispatcher requestDispatcher = request.getRequestDispatcher(modelView.getNameView());
                    for(Entry<String, Object> entry : modelView.getListeview().entrySet()){
                        request.setAttribute(entry.getKey(), entry.getValue());
                    }
                    requestDispatcher.forward(request, response);
                }
                else{
                    out.println("erreur de donnée");
                }
                String result = (String) returnValue;
        
                out.println("<p>" + result + "</p>");
                out.close();
            } catch (Exception e) {
                e.printStackTrace();
                out.println("something wrong"); // Gérer l'exception correctement, ne pas laisser vide
            }
        }
        
    }   

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }


    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }

    private void scanControllers(String packageName) throws Exception {
        try {

            // Charger le package et parcourir les classes
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            String path = packageName.replace('.', '/');
            URL resource = classLoader.getResource(path);
            if (resource == null) {
                throw new ServletException("Ldesolé  le : " + packageName + "n' existe pas");
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
                                Method[] methods= clazz.getMethods();

                                for (Method methode : methods) {
                                    if (methode.isAnnotationPresent(AnnotationGet.class)) {
                                        Mapping map = new Mapping(className, methode.getName());
                                        String valeur = methode.getAnnotation(AnnotationGet.class).value();
                                        if (urlMaping.containsKey(valeur)) {
                                            throw new Exception("double url" + valeur);
                                        } else {
                                            urlMaping.put(valeur, map);
                                        }
                                    } else if (methode.isAnnotationPresent(AnnotationGet.class)) {
                                        Mapping map = new Mapping(className, methode.getName());
                                        String valeur = methode.getAnnotation(AnnotationPost.class).value();
                                        if (urlMaping.containsKey(valeur)) {
                                            throw new Exception("double url" + valeur);
                                        } else {
                                            urlMaping.put(valeur, map);
                                        }
                                    }
                                }
                            }
                        } catch (ClassNotFoundException | ServletException e) {
                            e.printStackTrace();

                        } catch (Exception e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }
                    });
        } catch (Exception e) {
            e.printStackTrace();
            throw new Exception("tsy mi existe ilay dossier",e);
        }
    }
    private Object[] getMethodParameters(Method method, HttpServletRequest request) {
        Parameter[] parameters = method.getParameters();
        Object[] parameterValues = new Object[parameters.length];

        for (int i = 0; i < parameters.length; i++) {
            if (parameters[i].isAnnotationPresent(Param.class)) {
                Param param = parameters[i].getAnnotation(Param.class);
                String paramValue = request.getParameter(param.value());
                parameterValues[i] = paramValue; // Assuming all parameters are strings for simplicity
            }
        }

        return parameterValues;
    }
    
}

