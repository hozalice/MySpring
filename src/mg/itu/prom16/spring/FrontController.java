package mg.itu.prom16.spring;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
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
import mg.itu.prom16.annotations.Annotation_controlleur;
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
        scanControllers(packageName);
    }

    protected void processRequest(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        StringBuffer requestURL = request.getRequestURL();
        String[] requestUrlSplitted = requestURL.toString().split("/");
        String controllerSearched = requestUrlSplitted[requestUrlSplitted.length-1];
        
        PrintWriter out = response.getWriter();
        response.setContentType("text/html");

        if (!urlMaping.containsKey(controllerSearched)) {
            out.println("<p>"+"Aucune methode associee a ce chemin."+"</p>");
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
                if (returnValue instanceof String) {
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
                String result = (String) returnValue;
        
                out.println("<p>" + result + "</p>");
                out.close();
            } catch (Exception e) {
                e.printStackTrace(); // Gérer l'exception correctement, ne pas laisser vide
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

    private void scanControllers(String packageName) {
        try {

            // Charger le package et parcourir les classes
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            String path = packageName.replace('.', '/');
            URL resource = classLoader.getResource(path);
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

                                for(Method m : methods){
                                    if(m.isAnnotationPresent(AnnotationGet.class)){
                                        Mapping mapping =new Mapping(className , m.getName());
                                        AnnotationGet AnnotationGet = m.getAnnotation(AnnotationGet.class);
                                        String annotationValue = AnnotationGet.value();
                                        urlMaping.put(annotationValue, mapping);
                                    }
                                }
                            }
                        } catch (ClassNotFoundException e) {
                            e.printStackTrace();
                        }
                    });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}

