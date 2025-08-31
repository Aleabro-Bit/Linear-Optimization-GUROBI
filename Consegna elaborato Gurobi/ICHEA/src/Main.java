import java.io.*;
import java.util.*;
import com.gurobi.gurobi.*;

public class Main {
	//interi
	//numero cucine
	static int n;
	//numero reparti
	static int l;
	//numero materiali
	static int m;
	//profitto netto
	static int P;
	//tipologie di cucine da produrre
	static int s;
	//massimo decremento intero t e-esimo elemento
	static int e;
	//BIG M
	static int M=15;
	
	//vettori
	//qtà massima materiali
	static ArrayList<Integer> Q = new ArrayList<>();
	//tempo ogni reparto
	static ArrayList<Integer> t = new ArrayList<>();
	//prezzo vendita cucine
	static ArrayList<Integer> c = new ArrayList<>();
	//costo unitario cucine
	static ArrayList<Integer> p = new ArrayList<>();
	//quantità minima dei materiali per le cucine scelte
	static ArrayList<Integer> u = new ArrayList<>();
	//binary operator
	static GRBVar [] y = null;
	
	//matrici
	//tempo di lavorazione
	static ArrayList<ArrayList<Integer>> w = new ArrayList<>();
	//quantità necessaria alla lavorazione
	static ArrayList<ArrayList<Integer>> q = new ArrayList<>();
	
	public static void main(String[] args) {
		System.out.println("GRUPPO: <15>");
		System.out.println("Abronzino Alessandro\t Giammarco Ghidini\n\n");
		//leggo e salvo i dati da file
		fileReader();
		
		try {
			//creazione ambiente e modello
			GRBEnv env = new GRBEnv("ICHEA.log");
			//impostazione dei parametri (no visualizzazione video processo ottimizzazione)
			//disattivabile se si vogliono vedere i processi
			setParams(env);
			GRBModel model = new GRBModel(env);
			//INIZIALIZZAZIONE VARIABILI
			GRBVar [] x = aggiungiVariabili(model);
			//funzione obiettivo
			GRBLinExpr exp = new GRBLinExpr();
			aggiungiFunzioneObiettivo(model, x, exp);
			
			//vincolo 1
			aggiungiVincolo1(model, x, exp);
			//vincolo 2
			aggiungiVincolo2(model, x, exp);
			//vincolo 3
			aggiungiVincolo3(model, x, exp);

			//ottimizzazione modello
			model.optimize();
			//stampo a video i valori 
			System.out.println("\nQUESITO I:");
			stampaValori(model);

			//creazione rilassamento 
			GRBModel rilassamento = model.relax();
			//ottimizzazione rilassamento
			rilassamento.optimize();
			
			stampaRilassato(rilassamento);
			isUniqueSolution(rilassamento);
			isDegenere(rilassamento);
			isEqual(model, rilassamento);
			
			System.out.println("\nQUESITO 2;");
			//aggiunta nuovi vincoli
			//aggiungiVariabiliBinarie();
			aggiungiVariabiliBinary(model);
			aggiungiVincoloBinario(model, x, exp);
			aggiungiVincoloBinario2(model, exp);
			aggiungiVincoloProduzione(model, x, exp);
			//ottimizzazione model*
			model.update();
			model.optimize();
			stampaValori(model);
			
			
			System.out.println("\nQUESITO 3:");
			deltaP(rilassamento);
			//imposto il rilassamento = al rilassato del model* per trovare t
			setParams(env);
			rilassamento=model.relax();
			rilassamento.optimize();
			deltaT(rilassamento);
			deltaTInteger(model,env);
			
			rilascioMemoria(model, env);
		}
		catch(GRBException e){
			System.out.println("Error code: " + e.getErrorCode() + ". " + e.getMessage());
		}
	}
	/**
	 * Imposta i parametri risolutivi e disattiva la visualizzazione del processo di gurobi
	 * per una visione più sintetica
	 * @param env
	 * @throws GRBException
	 */
	private static void setParams(GRBEnv env) throws GRBException 
	{
		env.set(GRB.IntParam.Method, 0);
		env.set(GRB.IntParam.Presolve, 0);
		env.set(GRB.IntParam.OutputFlag, 0);
	}
	/**
	 * Verifico se le variabili in base valgono 0 per constatare se la soluzine è degenere
	 * (posto minore di 1e-10 per evitare errori di floating point)
	 * @param model
	 * @throws GRBException
	 */
	public static void isDegenere(GRBModel model) throws GRBException {
		
		for(GRBVar v: model.getVars()) {
			//Nota dalla documentazione di gurobi: VBasis restituisce un intero
			//se vale 0 -> in base; -1 -> non in base lower bound; -2 non in base upper bound ; -3 super-base?
			if(v.get(GRB.DoubleAttr.X)<=1e-10 && v.get(GRB.IntAttr.VBasis) == 0) {
				System.out.println("La soluzione e' degenere");
				return;
			}
		}
		  System.out.println("La soluzione non e' degenere");
	}
	
	private static void stampaValori(GRBModel model) throws GRBException
	{
		System.out.println("\nVALORE OTTIMO FUNZIONE OBIETTIVO:");
		if(model.get(GRB.IntAttr.Status) == GRB.Status.OPTIMAL){
			System.out.printf("Valore obiettivo: %.4f\n", model.get(GRB.DoubleAttr.ObjVal));

		}
		System.out.println("\nVARIABILI NON DI SLACK/SURPLUS");
		for(GRBVar v: model.getVars()) {
			System.out.printf("%s%.4f\n",v.get(GRB.StringAttr.VarName) , v.get(GRB.DoubleAttr.X)); //valore variabili non di slack	
		}
		System.out.println("\nVARIABILI SLACK/SURPLUS");
		for(GRBConstr c: model.getConstrs()) {
			//System.out.println(c.get(GRB.StringAttr.ConstrName) + " = " + c.get(GRB.DoubleAttr.Slack)); //valore slack
			System.out.printf("%s = %.4f\n",c.get(GRB.StringAttr.ConstrName) , c.get(GRB.DoubleAttr.Slack)); //valore slack

		}
	}
	private static void stampaRilassato(GRBModel rilassamento) throws GRBException
	{
		System.out.println("\nOTTIMO RILASSATO");
		if(rilassamento.get(GRB.IntAttr.Status) == GRB.Status.OPTIMAL){
			System.out.printf("Valore obiettivo: %.4f\n" , rilassamento.get(GRB.DoubleAttr.ObjVal));
		}
		System.out.println("\nVINCOLI ATTIVI");
		for(GRBConstr c: rilassamento.getConstrs()) {
			if(c.get(GRB.DoubleAttr.Slack)<=1e-10 && c.get(GRB.DoubleAttr.Slack)>=-1e-10 )
				System.out.printf("%s = %.4f\n",c.get(GRB.StringAttr.ConstrName) , c.get(GRB.DoubleAttr.Slack)); //valore slack
		}
		System.out.println("VARIABILI NON DI SLACK/SURPLUS");
		for(GRBVar v: rilassamento.getVars()) {
			System.out.printf("%s%.4f\n",v.get(GRB.StringAttr.VarName) , v.get(GRB.DoubleAttr.X)); //valore variabili non di slack	
		}
	}
	
	/**
	 * Aggiunge le variabili per le n cucine di m materiali diversi
	 * @param model
	 * @return GRBVar
	 * @throws GRBException
	 */
	private static GRBVar[] aggiungiVariabili(GRBModel model) throws GRBException 
	{
		GRBVar [] x = new GRBVar[n];
		for(int i=0; i<n; i++) {
				x[i] = model.addVar(0.0, GRB.INFINITY,0.0, GRB.INTEGER, "x[" + i + "]: " );
		}
		return x;
	}
	/**
	 * creazione della variabile decisionale binaria y[i]
	 * @param model
	 * @throws GRBException
	 */
	private static void aggiungiVariabiliBinary(GRBModel model) throws GRBException 
	{
		y = new GRBVar[n];
		for(int i=0; i<n; i++) {
				y[i] = model.addVar(0.0, 1,0.0, GRB.BINARY, "y[" + i + "]: " );
		}
	}
	
	// aggiunta F.O. 
	private static void aggiungiFunzioneObiettivo(GRBModel model, GRBVar[] x,GRBLinExpr exp) throws GRBException 
	{	
		exp= new GRBLinExpr();
		for (int i=0; i<l; i++) {
			exp.addConstant(t.get(i));
		}
		for (int i=0; i< n; i++) {
			for (int j=0; j<l;j++) {
				exp.addTerm(-w.get(i).get(j), x[i]);
			}
		}
		model.setObjective(exp, GRB.MINIMIZE);
	}
	/**
	 * Vincoli di tempo dei reparti
	 * @param model
	 * @param x
	 * @param exp
	 * @throws GRBException
	 */
	private static void aggiungiVincolo1(GRBModel model, GRBVar [] x, GRBLinExpr exp) throws GRBException 
	{
		for(int j=0; j< l; j++) {
			exp = new GRBLinExpr();
			for(int i=0; i<n;i++) {
				exp.addTerm(w.get(i).get(j),x[i]);
			}
			model.addConstr(exp, GRB.LESS_EQUAL, t.get(j), "capacita' tempo_" + j);
			
		}
	}
	/**
	 * Vincoli di disponibilità dei materiali
	 * @param model
	 * @param x
	 * @param exp
	 * @throws GRBException
	 */
	private static void aggiungiVincolo2(GRBModel model, GRBVar [] x, GRBLinExpr exp) throws GRBException 
	{
		for(int i=0; i< m; i++) {
			exp = new GRBLinExpr();
			for(int j=0; j<n;j++) {
				exp.addTerm(q.get(j).get(i),x[j]);
			}
			model.addConstr(exp, GRB.LESS_EQUAL, Q.get(i), "quantita' materiali_" + i);		
			}
	}
	/**
	 * Vincolo profitto netto
	 * @param model
	 * @param x
	 * @param exp
	 * @throws GRBException
	 */
	private static void aggiungiVincolo3(GRBModel model, GRBVar [] x, GRBLinExpr exp) throws GRBException 
	{
		exp = new GRBLinExpr();
		for (int i=0; i<n;i++) {
			exp.addTerm(c.get(i)-p.get(i), x[i]);
		}
		model.addConstr(exp, GRB.GREATER_EQUAL, P, "Profitto netto");
	}
	/**
	 * Aggiungo il vincolo che impone x<=yM con M scelta sufficientemente grande
	 * se y[i]=1 allora x[i] viene scelta, invece se y[i]=0 x[i] viene impostata a zero e quindi non viene presa 
	 * @param model,x, exp
	 * @throws GRBException
	 */
	private static void aggiungiVincoloBinario(GRBModel model, GRBVar [] x, GRBLinExpr exp) throws GRBException 
	{
		for (int i=0; i<n;i++) {
			exp = new GRBLinExpr();
			exp.addTerm(1, x[i]);
			exp.addTerm(-M, y[i]);
			model.addConstr(exp, GRB.LESS_EQUAL, 0, "Cucine scelte_" + i);
		}
	}
	/**
	 * Si crea un vincolo in cui la somma delle y[i] sia uguale alle s cucine che voglio produrre
	 * @param model
	 * @param exp
	 * @throws GRBException
	 */
	private static void aggiungiVincoloBinario2(GRBModel model, GRBLinExpr exp) throws GRBException 
	{	exp = new GRBLinExpr();
		for (int i=0; i<n;i++) {
			
			exp.addTerm(1, y[i]);
		}
		model.addConstr(exp, GRB.EQUAL, s, "ToT cucine scelte:" + s);

	}
	
	public static void rilascioMemoria(GRBModel model, GRBEnv env) throws GRBException {
		model.dispose();
		env.dispose();
	}
	/**
	 * Se x[i] è stata scelta (cioè se y[i]=1) allora aggiungo il vincolo di produrre minimo u(i) pezzi
	 * @param model,x,exp
	 * @throws GRBException
	 */
	private static void aggiungiVincoloProduzione(GRBModel model, GRBVar [] x, GRBLinExpr exp) throws GRBException 
	{
		for (int i=0; i<n;i++) {
				exp = new GRBLinExpr();
				exp.addTerm(1, x[i]);
				exp.addTerm(-u.get(i), y[i]);
				model.addConstr(exp, GRB.GREATER_EQUAL, 0, "u pezzi_"+i);
		}
	}
	/**
	 * Verifico se ho un'unica soluzione 
	 * @param model
	 * @throws GRBException
	 */
	 public static void isUniqueSolution(GRBModel model) throws GRBException {
		 //alternativamente si poteva controllare se ci fossero RC = 0 delle variabili non in base
		 if((model.get(GRB.IntAttr.SolCount) == 1)) {
			 System.out.println("\nho un'unica soluzione");
		 }
		 else  System.out.println("\nho piu' di una soluzione");
	    }
	 /**
	  * Verifico se i l'ottimo dell'Intero coincide col rilassato
	  * @param model
	  * @param rilassamento
	  * @throws GRBException
	  */
	 public static void isEqual (GRBModel model, GRBModel rilassamento) throws GRBException {
		 //TODO aggiungere condizioni che variabili in base intere
		 if( model.get(GRB.DoubleAttr.ObjVal) == rilassamento.get(GRB.DoubleAttr.ObjVal))
			 System.out.println("Il rilassato coincide con l'intero");
		 		//TODO aggiungere relazione tra i due valori
		 	 System.out.println("il rilassato e l'intero non coincidono");
	 }
	 /**
	  * Metodo per stabilire la variazione che P potrebbe assumere senza far variare la base
	  * ottima del rilassato attraverso l'analisi di sensitività tramite l'utilizzo dei metodi
	  * SARHSup/SARHLow della libreria di Gurobi
	  * @param model
	  * @throws GRBException
	  */
	 public static void  deltaP(GRBModel model) throws GRBException {
		 System.out.println("\nANALISI DI SENSITIVITA':");
		 String str1 ="Intervallo prezzo massimo delta P: (" + model.getConstrByName("Profitto netto").get(GRB.DoubleAttr.SARHSLow) + " , " + model.getConstrByName("Profitto netto").get(GRB.DoubleAttr.SARHSUp) +  "]";
		 System.out.println(str1.replaceAll("1.0E100 ", "INF"));
		 double delta = model.getConstrByName("Profitto netto").get(GRB.DoubleAttr.SARHSUp)- model.getConstrByName("Profitto netto").get(GRB.DoubleAttr.SARHSLow);
		 String str2 = ""+delta;
		 System.out.println("delta P = "+str2.replaceAll("1.0E100", "INF")+"\n");
	 }
	 /**
	  * Analisi di sensitività effettuato sul rilassato per calcolare la massima variazione di te
	  * (non da implementare)
	  * @param model
	  * @throws GRBException
	  */
	 public static void  deltaT(GRBModel model) throws GRBException {
		 int index = e-1;
		 //System.out.println("\n prezzi ombra t_e" + model.getConstrByName("capacita' tempo_" +index).get(GRB.DoubleAttr.Pi));
		 System.out.println("\nANALISI DI SENSITIVITA'(rilassato):");
		 System.out.printf("Valore minimo t_%d: %.4f \n", index,model.getConstrByName("capacita' tempo_" +index).get(GRB.DoubleAttr.SARHSLow));
	 }
	 /**
	  * Il metodo decrementa il vincolo te progressivamente fino al valore massimo entro cui non varia la 
	  * funzione obiettivo del problema di programmazione lineare intera
	  * @param model
	  * @throws GRBException
	  */
	 public static void  deltaTInteger(GRBModel model,GRBEnv env) throws GRBException {
		 setParams(env);
		 int index = e-1;
		 int decremento = 0;
		 double obj = model.get(GRB.DoubleAttr.ObjVal);
		 int[] decrementSteps = {50, 10, 1};
		 
		 for (int step : decrementSteps) {
			 while(Math.abs(obj - model.get(GRB.DoubleAttr.ObjVal)) <= 1e-10) {
				 GRBConstr constr = model.getConstrByName("capacita' tempo_" + index);
			     double currentRHS = constr.get(GRB.DoubleAttr.RHS);
			     constr.set(GRB.DoubleAttr.RHS, currentRHS - step);
			        model.update();
			        model.optimize();
			        decremento += step;
			 }
			 decremento -= step;
			 GRBConstr constr = model.getConstrByName("capacita' tempo_" + index);
		     double currentRHS = constr.get(GRB.DoubleAttr.RHS);
		     constr.set(GRB.DoubleAttr.RHS, currentRHS + step);
		        model.update();
		        model.optimize();
		 }
		 
		 System.out.printf("\nIl valore t_%d puo' essere decrementato di %d affinche' non cambi la f.o di M*PLI", index, decremento);
		 int Tdecr =t.get(index)-decremento;
		 System.out.printf("\nvalore minimo t_%d: %d", index, Tdecr);
	 }
	 
	/**
	 * legge file di testo
	 */
	public static void fileReader(){
		File file = new File("src/instance-2.txt");
		try(Scanner scan = new Scanner(file)){
			while(scan.hasNextLine()) {
			String[] str = scan.nextLine().split("\\s+");
				processLine(str,scan);
			}
			}
		catch(FileNotFoundException e){
			e.printStackTrace();
		}
		catch(NumberFormatException e) {
			e.printStackTrace();
		}
	}
	/**
	 * associa i valori letti dal file 
	 */
	public static void processLine(String[]str,Scanner scan) {
		switch (str[0]) {
		case "n":
			n = Integer.parseInt(str[1]);
			//System.out.println("n: " +n);
			break;
		case "l":
			l = Integer.parseInt(str[1]);
			//System.out.println("l: "+l);
			break;
		case "m":
			m = Integer.parseInt(str[1]);
			//System.out.println("m: "+m);
			break;
		case "P":
			P = Integer.parseInt(str[1]);
			//System.out.println("P: "+P);
			break;
		case "s":
			s = Integer.parseInt(str[1]);
			//System.out.println("s: "+s);
			break;
		case "e":
			e = Integer.parseInt(str[1]);
			//System.out.println("e: "+ e);
			break;
		case "Q":
			for(int i= 1; i< str.length; i++) {
				Q.add(Integer.parseInt(str[i]));
		}
			//System.out.println("Q: "+Q);
			break;
		case "t":
			for(int i= 1; i< str.length; i++) {
				t.add(Integer.parseInt(str[i]));
		}
			//System.out.println("t: "+t);
			break;
		case "c":
			for(int i= 1; i< str.length; i++) {
				c.add(Integer.parseInt(str[i]));
		}
			//System.out.println("c: "+c);
			break;
		case "p":
			for(int i= 1; i< str.length; i++) {
				p.add(Integer.parseInt(str[i]));
		}
			//System.out.println("p: "+p);
			break;
		case "u":
			for(int i= 1; i< str.length; i++) {
				u.add(Integer.parseInt(str[i]));
		}	
			//System.out.println("u: "+ u);
			break;
		case "w":
			while(scan.hasNextInt()) { 
				str = scan.nextLine().split("\\s+");
				ArrayList<Integer> righe = new ArrayList<>();
				for(int j=0;j<str.length; j++) {
					righe.add(Integer.parseInt(str[j]));
				}
			
				w.add(righe);
		}	
			/*System.out.println("w: ");
			for (ArrayList<Integer> righe : w) {
			for (int elemento : righe) {
				System.out.print(elemento + " ");
			}
			System.out.println();
		}*/
			//System.out.println("w: "+ w);
			break;
		case "q":
			while(scan.hasNextInt()) { 
				str = scan.nextLine().split("\\s+");
				ArrayList<Integer> righe = new ArrayList<>();
				for(int j=0;j<str.length; j++) {
					righe.add(Integer.parseInt(str[j]));
				}
			
				q.add(righe);
		}
			/*System.out.println("q: ");
			for (ArrayList<Integer> righe : q) {
			for (int elemento : righe) {
				System.out.print(elemento + " ");
			}
			System.out.println();
		}*/
			//System.out.println("q: "+ q);
			break;
		}
	}

}
