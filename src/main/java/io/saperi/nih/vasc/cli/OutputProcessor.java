/*Copyright 2021 Cognitive Medical Systems*/
package io.saperi.nih.vasc.cli;


import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OutputProcessor {
    private boolean verbose = false;
    private boolean unmuted = true;
    boolean expectionsLogged = false;


    public OutputProcessor()
    {

    }

    public OutputProcessor(boolean verbose, boolean unmuted)
    {
        this.verbose = verbose;
        this.unmuted = unmuted;
    }

    public boolean hasLoggedExceptions() {return expectionsLogged;};

    public boolean isVerbose() {
        return verbose;
    }

    public boolean isUnmuted() {
        return unmuted;
    }

    public void setUnmuted(boolean unmutted) {
        this.unmuted = unmutted;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public void println(String msg) {
        if (unmuted) {
            System.out.println(msg);
        } else {
            log.debug(msg);
        }
    }

    public void vprintln(String msg) {
        if (verbose) {
            if (unmuted) {
                System.out.println(msg);
            } else {
                log.debug(msg);
            }
        }
    }

    public void printException(String msg) {
        expectionsLogged = true;
        if (unmuted) {
            System.out.println(msg);
        } else {
            System.err.println(msg);
        }
    }

    public void printException(Exception exp) {
        expectionsLogged = true;
        if (unmuted) {
            exp.printStackTrace(System.out);
        } else {
            exp.printStackTrace(System.err);

        }
    }

}
