/*
 * A simple port monitor.
 *
 */
package com.emc.tools.portmon;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author chitas
 */
public class PortMon {
    
    private static final String LISTENING = "LISTENING";

    private static final Logger logger = Logger.getLogger(PortMon.class.getName());

    static class Port {

        String raw;
        String localHost;
        String localPort;
        String pid;
        String state;

        @Override
        public String toString() {
            return this.raw == null ? "" : this.raw;
        }
    }

    private static final List<String> netstatPrefix = new LinkedList<>(
            Arrays.<String>asList(
                    "cmd", "/C", "netstat", "-anop", "tcp", "|", "findstr"));

    private static final List<String> killPidPrefix = new LinkedList<>(
            Arrays.<String>asList(
                    "cmd", "/C", "taskkill", "/F", "/PID"));

    // tasklist /V /FO LIST /FI "PID eq 36588"
    private static final List<String> pidInfoPrefix = new LinkedList<>(
            Arrays.<String>asList(
                    "cmd", "/C", "tasklist", "/V", "/FO", "LIST", "/FI"));

    static List<Port> getPorts(boolean listeningOnly, String... ports) {
        List<Port> portsList = new LinkedList<>();
        List<Integer> portInts = new LinkedList<>();
        for (String port : ports) {
            try {
                portInts.add(Integer.parseInt(port));
            } catch (Exception e) {
            }
        }
        List<String> netstat = new LinkedList<>(netstatPrefix);
        if (portInts.size() > 0) {
            for (Integer portInt : portInts) {
                netstat.add("/C::" + portInt);
            }
        } else {
            netstat.add("/R");
            netstat.add(".*");
        }
        ProcessBuilder netstatProcessBuilder = new ProcessBuilder(netstat);
        BufferedReader br = null;
        try {
            final Process netstatProcess = netstatProcessBuilder.start();
            InputStream inputStream = netstatProcess.getInputStream();
            br = new BufferedReader(new InputStreamReader(inputStream));
            String raw;

            while ((raw = br.readLine()) != null) {
                Port p = new Port();
                p.raw = raw;
                String[] parts = raw.split("\\s+");
                if (parts.length == 6) {
                    if (!listeningOnly || LISTENING.equals(parts[4])) {
                        String[] hostAndPort = parts[2].split(":");
                        p.localHost = hostAndPort[0];
                        p.localPort = hostAndPort[1];
                        p.state = parts[4];
                        p.pid = parts[5];
                        portsList.add(p);
                    }
                }
            }
            netstatProcess.waitFor();
        } catch (IOException | InterruptedException ex) {
            logger.log(Level.SEVERE, null, ex);
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException ex) {
                }
            }
        }

        return portsList;
    }


    static void killProcess(String pidString) {
        List<String> killPid = new LinkedList<>(killPidPrefix);
        killPid.add(pidString.trim());
        ProcessBuilder killPidProcessBuilder = new ProcessBuilder(killPid);
        try {
            Process killPidProcess = killPidProcessBuilder.start();
            killPidProcess.waitFor();
        } catch (IOException | InterruptedException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }
    
    static String processInfo(String pidString) {
        List<String> pidInfo = new LinkedList<>(pidInfoPrefix);
        pidInfo.add("PID eq " + pidString.trim());
        ProcessBuilder pidInfoProcessBuilder = new ProcessBuilder(pidInfo);
        StringBuilder info = new StringBuilder();
        BufferedReader br = null;
        try {
            final Process pidInfoProcess = pidInfoProcessBuilder.start();
            InputStream inputStream = pidInfoProcess.getInputStream();
            br = new BufferedReader(new InputStreamReader(inputStream));
            String raw;

            while ((raw = br.readLine()) != null) {
                info.append(raw+"\n");
            }
            pidInfoProcess.waitFor();
        } catch (IOException | InterruptedException ex) {
            logger.log(Level.SEVERE, null, ex);
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException ex) {
                }
            }
        }
        return info.toString();
    }

    public static void main(String[] args) {
        List<Port> ports = getPorts(true, args);
        for (Port port : ports) {
            System.out.println(port.pid);
        }
    }
}
