package tcp_diccionarioserver;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author elian
 */
public class TCP_DiccionarioServer2_Multihilo
{
    static final int NUM_PUERTO = 7890;

    public static void main(String[] args)
    {
        try ( var socketServidor = new ServerSocket(NUM_PUERTO))
        {
            System.out.printf("--- Creado socket de servidor en puerto %d. Esperando conexiones de clientes. ---\n", NUM_PUERTO);

            while (true)
            {
                // Acepta una conexión de cliente tras otra
                var socketConNuevoCliente = socketServidor.accept();

                System.out.printf("Cliente conectado desde %s:%d.\n",
                        socketConNuevoCliente.getInetAddress().getHostAddress(),
                        socketConNuevoCliente.getPort());

                var hiloSesion = new HiloServidor(socketConNuevoCliente);
                hiloSesion.start();
            }
        }
        catch (IOException ex)
        {
            Logger.getLogger(TCP_DiccionarioServer2_Multihilo.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
