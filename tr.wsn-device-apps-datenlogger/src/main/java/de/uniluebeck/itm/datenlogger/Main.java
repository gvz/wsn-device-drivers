package de.uniluebeck.itm.datenlogger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import org.apache.commons.cli.*;

public class Main {
	
	private static double version = 0.1;
	
	public static void main(String[] args) throws IOException {
		// create Options object
		Option help_option = new Option( "help", "print this message" );
		Option version_option = new Option( "version", "print the version information" );
		Option interactive = new Option("i", "interactive mode");
		
		Options options = new Options();
		
		options.addOption(help_option);
		options.addOption(version_option);
		options.addOption(interactive);

		// add options for Datenlogger
		options.addOption("port", true, "port");
		options.addOption("server", true, "server");
		options.addOption("location", true, "Ausgabeziel der Daten, die geloggt werden");
		options.addOption("klammer_filter", true, "Kombination der Filtertypen: (Datentyp,Beginn,Wert)-Filter");
		options.addOption("regex_filter", true, "Kombination der Filtertypen: Regular Expression-Filter");
		
		// for help statement
		HelpFormatter formatter = new HelpFormatter();

		CommandLineParser parser = new GnuParser();
		CommandLine cmd = null;
		try {
			cmd = parser.parse(options, args);
		} catch (ParseException e) {
			System.out.println("Diese Option gibt es nicht.");
		}
		if(cmd != null){
			Datenlogger datenlogger = new Datenlogger();
			
			if(cmd.hasOption("i")){
				new Listener(datenlogger).start();
			}
			
			//standard-options
			if(cmd.hasOption("help")){
				System.out.println("Aufrufbeispiele:");
				System.out.println("Datenlogger: startlog -filter 0a, 0b, 54 -location filename.txt -port 141.83.1.546:1282");
				System.out.println("");
				formatter.printHelp("help", options);
			}
			if(cmd.hasOption("version")){
				System.out.println(version);
			}
			
			//der Datenlogger
			if(args[0].equals("getloggers")) {
				System.out.println("starte Datenlogger...");
	
				String port = cmd.getOptionValue("port");
				String server = cmd.getOptionValue("server");
				
				datenlogger.setPort(port);
				datenlogger.setServer(server);
				datenlogger.getloggers();
				
			}else if(args[0].equals("startlog")) {
				System.out.println("starte Datenlogger...");
				
				String port = cmd.getOptionValue("port");
				String server = cmd.getOptionValue("server");
				String klammer_filter = cmd.getOptionValue("klammer_filter");
				String regex_filter = cmd.getOptionValue("regex_filter");
				String location = cmd.getOptionValue("location");
				
				datenlogger.setPort(port);
				datenlogger.setServer(server);
				datenlogger.setKlammer_filter(klammer_filter);
				datenlogger.setRegex_filter(regex_filter);
				datenlogger.setLocation(location);
				datenlogger.startlog();
				
			}
		}
	}
}
