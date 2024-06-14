package mg.itu.prom16.map;

import java.util.HashMap;

public class ModelView {
    String nameView;
    HashMap<String,Object> listeview=new HashMap<>();
    public ModelView(String nameView, HashMap<String, Object> listeview) {
        this.nameView = nameView;
        this.listeview = listeview;
    }
    public ModelView(){

    }
    public String getNameView() {
        return nameView;
    }
    public void setNameView(String nameView) {
        this.nameView = nameView;
    }
    public HashMap<String, Object> getListeview() {
        return listeview;
    }
    public void setListeview(HashMap<String, Object> listeview) {
        this.listeview = listeview;
    }
    public void addObject(String anarany , Object viewassocier){
        listeview.put(anarany, viewassocier);
    }
    
}
