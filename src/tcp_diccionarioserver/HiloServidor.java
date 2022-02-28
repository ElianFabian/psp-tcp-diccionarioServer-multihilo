package tcp_diccionarioserver;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * @author Elián
 */
class HiloServidor extends Thread
{
    //<editor-fold desc="Atributos" defaultstate="collapsed">
    private final Socket socketComunicacion;
    private Estado estado = Estado.NORMAL;

    static private final String COD_TEXTO = "UTF-8";
    static final int NUM_PUERTO = 7890;

    static final String STR_PAT_LETRAS = "[a-zA-Z]+";

    static final Pattern patConsulta = Pattern.compile("\\?(" + STR_PAT_LETRAS + ")");
    static final Pattern patAsignacion = Pattern.compile("!(" + STR_PAT_LETRAS + ")=([a-zA-Z ]+)");
    static final Pattern patPalabrasComiezanCon = Pattern.compile("\\?>(" + STR_PAT_LETRAS + ")");
    static final Pattern patPalabrasTerminanCon = Pattern.compile("\\?<(" + STR_PAT_LETRAS + ")");
    static final Pattern patAisignacionConDefs = Pattern.compile("(" + STR_PAT_LETRAS + ")=([a-zA-Z ]+)");

    static final String STR_PROMPT = "dic> ";
    static final String STR_EXIT = "exit";
    static final String STR_QUIT = "quit";
    static final String STR_BYE = "bye";
    static final String STR_DEFS = "!defs";
    static final String STR_NOTFOUND = "NOTFOUND: ";
    static final String STR_REPL = "REPL:";
    static final String STR_NEWENT = "NEWENT:";
    static final String STR_ERROR = "ERR: comando incorrecto";

    static final HashMap<String, String> diccionario = new HashMap<>();
    //</editor-fold>

    HiloServidor(Socket socketComunicacion)
    {
        this.socketComunicacion = socketComunicacion;
    }

    @Override
    public void run()
    {
        try ( var isDeCliente = socketComunicacion.getInputStream();
              var osACliente = socketComunicacion.getOutputStream();
              var isrDeCliente = new InputStreamReader(isDeCliente, COD_TEXTO);
              var brDeCliente = new BufferedReader(isrDeCliente);
              var oswACliente = new OutputStreamWriter(osACliente, COD_TEXTO);
              var bwACliente = new BufferedWriter(oswACliente))
        {
            String lineaRecibida;
            while ((lineaRecibida = brDeCliente.readLine()) != null)
            {
                if (analizarLineaRecibida(lineaRecibida, bwACliente)) break;

                bwACliente.newLine();
                bwACliente.flush();
            }
        }
        catch (IOException ex)
        {
            Logger.getLogger(HiloServidor.class.getName()).log(Level.SEVERE, null, ex);
        }
        finally
        {
            if (socketComunicacion != null) try
            {
                socketComunicacion.close();
                System.out.printf("Cliente desconectado desde %s:%d.\n",
                        socketComunicacion.getInetAddress().getHostAddress(),
                        socketComunicacion.getPort());
            }
            catch (IOException ex)
            {
                Logger.getLogger(HiloServidor.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    /**
     * Analiza el contenido de la línea recibida y escribe la correspondiente
     * respuesta.
     *
     * @return Devuelve verdadero en caso de recibir "exit"/"quit" en
     * lineaRecibida para salir del bucle.
     */
    private boolean analizarLineaRecibida(final String lineaRecibida, final BufferedWriter bwACliente) throws IOException
    {
        Matcher m;
        boolean error = false;

        switch (estado)
        {
            case NORMAL:
                // <editor-fold desc="Salir: bye, quit" defaultstate="collapsed">
                if (lineaRecibida.equals(STR_EXIT) || lineaRecibida.equals(STR_QUIT))
                {
                    bwACliente.write(STR_BYE);
                    return true;
                }
                // </editor-fold>

                // <editor-fold desc="Consultar palabra: ?palabra" defaultstate="collapsed">
                if ((m = patConsulta.matcher(lineaRecibida)).matches())
                {
                    String palabra = m.group(1);
                    if (diccionario.containsKey(palabra))
                    {
                        //               dic> palabra:significado
                        bwACliente.write(STR_PROMPT + palabra + ":" + diccionario.get(palabra));
                    }
                    else bwACliente.write(STR_NOTFOUND + palabra);
                }
                // </editor-fold>

                // <editor-fold desc="Asignar significado: !palabra=significado" defaultstate="collapsed">
                else if ((m = patAsignacion.matcher(lineaRecibida)).matches())
                {
                    String palabra = m.group(1);
                    String significado = m.group(2);

                    diccionario.put(palabra, significado);
                    bwACliente.write(STR_PROMPT + "OK, " + palabra + "=" + significado);
                }
                // </editor-fold>

                //<editor-fold desc="Palabras que empiezan con: ?>comienzo" defaultstate="collapsed">     
                else if ((m = patPalabrasComiezanCon.matcher(lineaRecibida)).matches())
                {
                    String comienzo = m.group(1);

                    final StringBuilder palabrasCoincidentes = new StringBuilder("");

                    diccionario.forEach((p, s) ->
                    {
                        if (p.startsWith(comienzo)) palabrasCoincidentes.append(p).append(":").append(s).append("\n");
                    });
                    bwACliente.write(palabrasCoincidentes.toString());
                }
                //</editor-fold>

                //<editor-fold desc="Palabras que terminan con: ?<final" defaultstate="collapsed">
                else if ((m = patPalabrasTerminanCon.matcher(lineaRecibida)).matches())
                {
                    String fin = m.group(1);

                    final StringBuilder palabrasCoincidentes = new StringBuilder("");

                    diccionario.forEach((p, s) ->
                    {
                        if (p.endsWith(fin)) palabrasCoincidentes.append(p).append(":").append(s).append("\n");
                    });
                    bwACliente.write(palabrasCoincidentes.toString());
                }
                //</editor-fold>

                //<editor-fold desc="Asignar varias definiciones a la vez: !defs" defaultstate="collapsed">
                else if (lineaRecibida.equals(STR_DEFS)) estado = Estado.DEFS;
                //</editor-fold>

                else error = true;

                break;
            case DEFS:
                //<editor-fold desc="Asignar significado: palabra=significado" defaultstate="collapsed">
                if ((m = patAisignacionConDefs.matcher(lineaRecibida)).matches())
                {
                    String palabra = m.group(1);
                    String significado = m.group(2);

                    if (diccionario.containsKey(palabra))
                    {
                        diccionario.put(palabra, significado);
                        bwACliente.write(STR_REPL + palabra + ":" + diccionario.get(palabra));
                    }
                    else
                    {
                        diccionario.put(palabra, significado);
                        bwACliente.write(STR_NEWENT + palabra + ":" + significado);
                    }
                }
                //</editor-fold>

                //<editor-fold desc="Finalizar estado" defaultstate="collapsed">
                // Cuando se presiona ENTER sin escribir nada se vuelve al estado NORMAL
                else if (lineaRecibida.length() == 0) estado = Estado.NORMAL;
                //</editor-fold>

                else error = true;

                break;
        }

        // <editor-fold desc="Comando incorrecto"  defaultstate="collapsed">
        if (error) bwACliente.write(STR_ERROR);
        // </editor-fold>

        return false;
    }
}
