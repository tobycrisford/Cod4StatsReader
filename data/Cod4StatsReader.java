import java.io.*;
import java.sql.*;
import javax.swing.*;
import java.util.*;
import java.util.Dictionary.*;

public class Cod4StatsReader extends Thread
{
  private int interval;
  private String database, filepath;

  public static void main(String args[])
  {
    System.out.println("\nWelcome to cod4 log reader\n");
    int interval;
    String database, filepath;
    try
    {
      BufferedReader config = new BufferedReader(new FileReader("logreader.cfg"));
      interval = Integer.parseInt(config.readLine());
      database = config.readLine();
      filepath = config.readLine();
    }
    catch (Exception e)
    {
      try
      {
        interval = 3600000 * Integer.parseInt(JOptionPane.showInputDialog("Welcome to Cod4StatsReader - designed by the NAF Clan.  Please enter in the box below, the interval in hours which you want between updates"));
      }
      catch (NumberFormatException nf)
      {
        JOptionPane.showMessageDialog(null, "ERROR - You must enter a valid integer\nCod4StatsReader will use a default value of 1 hour which you can change by modifying the config file");
        interval = 3600000;
      }
      database = JOptionPane.showInputDialog("Please enter the name of the database which you wish Cod4StatsReader to store the stats in:");
      JOptionPane.showMessageDialog(null, "On the following screen please open the cod4 log file");
      JFileChooser chooser = new JFileChooser();
      if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION)
      {
        filepath = chooser.getSelectedFile().toString();
      }
      else
      {
        JOptionPane.showMessageDialog(null, "ERROR - My team of highly trained ninja monkeys need a valid file to open, Cod4StatsReader will now exit");
        return;
      }
      try
      {
        BufferedWriter config = new BufferedWriter(new FileWriter("logreader.cfg"));
        String sinterval = Integer.toString(interval);
        config.write(sinterval, 0, sinterval.length());
        config.newLine();
        config.write(database, 0, database.length());
        config.newLine();
        config.write(filepath, 0, filepath.length());
        config.newLine();
        config.flush();
      }
      catch (IOException io)
      {
        JOptionPane.showMessageDialog(null, "My team of highly trained ninja monkeys were unable to write data to the configuration file - you will have to re-enter settings next time you run the program");
      }
    }
    Cod4StatsReader reader = new Cod4StatsReader(interval, database, filepath);
    reader.start();
  }

  public Cod4StatsReader(int interval, String database, String filepath)
  {
    this.interval = interval;
    this.database = database;
    this.filepath = filepath;
  }

  public void run()
  {
    Connection connection;
    try
    {
      Class.forName("sun.jdbc.odbc.JdbcOdbcDriver");
      connection = DriverManager.getConnection(database, "", "");
    }
    catch (Exception e)
    {
      JOptionPane.showMessageDialog(null, "ERROR - My team of highly trained ninja monkeys were unable to connect to the database: " + database + "\n\nPlease ensure that your database is a registered data source (to register it on a windows machine go to Administritive tools in the control panel)");
      return;
    }
    while (true)
    {
      BufferedReader in;
      Hashtable table = new Hashtable(100, (float) (0.75));
      try
      {
        in = new BufferedReader(new FileReader(filepath));
        for (String line = in.readLine();line != null;line = in.readLine())
        {
          try
          {
            if (line.indexOf(";") != -1 && line.indexOf("-----") == -1)
            {
              int[] colons = new int[12];
              colons[0] = line.indexOf(";");
              for (int i = 1;i < 12;i++)
              {
                colons[i] = line.indexOf(";", colons[i - 1] + 1);
              }
              String identifier = line.substring(colons[0] - 1, colons[0]);
              if (identifier.equals("K") || identifier.equals("D"))
              {
                String vname = line.substring(colons[3] + 1, colons[4]);
                String kname = line.substring(colons[7] + 1, colons[8]);
                Player victim = getPlayer(vname, table);
                if (vname.equals(kname) || kname.equals(""))
                {
                  victim.addDeath(true);
                }
                else
                {
                  Player killer = getPlayer(kname, table);
                  try
                  {
                    if (line.substring(colons[8] + 1, colons[9]).equals("none"))
                    {
                      killer.addKill("none", false);
                    }
                    else
                    {
                      killer.addKill(line.substring(colons[8] + 1, line.indexOf("_", colons[8])), line.substring(colons[11] + 1).equals("head"));
                    }
                  }
                  catch (TooManyWeaponsException e)
                  {
                    JOptionPane.showMessageDialog(null, "URGENT NOTICE:  My team of highly trained ninja monkeys have encountered too many weapons mentioned in the log file.\nThe log file reader will still run but kills with the extra weapons will be ignored, please contact the ninja monkey ringmaster\n\n" + e.toString());
                  }
                  victim.addDeath(false);
                }
              }
            }
          }
          catch (StringIndexOutOfBoundsException sobe)
          {
            System.out.println("\nERROR - My highly trained ninja monkeys encountered a line which did not match the expected format, the program will continue but this line will be ignored: \n" + line);
          }
        }
      }
      catch (IOException e)
      {
        JOptionPane.showMessageDialog(null, "ERROR - My team of highly trained ninja monkeys encountered a problem reading the Cod4 log file - exiting program");
        break;
      }
      try
      {
        Enumeration players = table.elements();
        Statement statement = connection.createStatement(ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_UPDATABLE);
        for (Player player = (Player) (players.nextElement());players.hasMoreElements();player = (Player) (players.nextElement()))
        {
          boolean inserting = false;
          String name = Remover.remove(player.getName(), "'");
          int kills = player.getTotalKills();
          int deaths = player.getDeaths();
          ResultSet record = statement.executeQuery("SELECT * From Stats WHERE Player = '" + name + "'");
          if (!record.next())
          {
            record.moveToInsertRow();
            inserting = true;
          }
          record.updateString("Player", name);
          record.updateInt("Kills", kills);
          record.updateInt("Deaths", deaths);
          if (deaths > 0)
          {
            try
            {
              record.updateDouble("Kill/Death Ratio", Rounder.round((double) (kills) / (double) (deaths), 2));
            }
            catch (NumberFormatException n)
            {
              record.updateDouble("Kill/Death Ratio", (double) (kills) / (double) (deaths));
            }
          }
          record.updateInt("Kill Streak", player.getKillStreak());
          record.updateInt("Suicides", player.getSuicides());
          record.updateInt("Headshots", player.getHeadshots());
          String[][] wkills = player.getKills();
          for (int j = 0;j < 50 && wkills[j][0] != null;j++)
          {
            try
            {
              record.updateInt(wkills[j][0], Integer.parseInt(wkills[j][1]));
            }
            catch (SQLException e)
            {
              JOptionPane.showMessageDialog(null, "An error occured while searching for weapon: " + wkills[j][0] + " in the database - The weapon could be a new release from Activision, if so please add a new field to the Stats database entitled: " + wkills[j][0]);
            }
          }
          if (inserting)
          {
            record.insertRow();
          }
          else
          {
            record.updateRow();
          }
        }
        System.out.println("\nThe database has been updated successfully");
      }
      catch (SQLException e)
      {
        JOptionPane.showMessageDialog(null, "ERROR - My team of highly trained ninja monkeys encountered a problem writing the stats to the database - exiting program");
        try
        {
          in.close();
        }
        catch (IOException io)
        {
          JOptionPane.showMessageDialog(null, "ERROR - My team of highly trained ninja monkeys were unable to close the input stream from the server file");
        }
        e.printStackTrace();
        break;
      }
      try
      {
        in.close();
      }
      catch (IOException e)
      {
        JOptionPane.showMessageDialog(null, "ERROR - My team of highly trained ninja monkeys were unable to close the input stream from the server file, the program will attempt to continue as normal");
      }
      table = null;
      try
      {
        Thread.sleep(interval);
      }
      catch (InterruptedException e)
      {
        JOptionPane.showMessageDialog(null, "ERROR - My team of highly trained insomniac ninja monkeys were unable to sleep - exiting program...");
        break;
      }
    }
  }

  private static Player getPlayer(String name, Hashtable table)
  {
    Player value = (Player) (table.get(name));
    if (value == null)
    {
      value = new Player(name);
      table.put(name, value);
    }
    return value;
  }
}

class Player
{
  private int deaths, suicides, headshots, tempkillstreak, killstreak;
  private String name;
  private String[][] kills = new String[50][2];

  public Player(String name)
  {
    this.name = name;
  }

  public void addDeath(boolean suicide)
  {
    deaths++;
    if (suicide)
    {
      suicides++;
    }
    tempkillstreak = 0;
  }

  public void addKill(String weapon, boolean headshot) throws TooManyWeaponsException
  {
    tempkillstreak++;
    if (tempkillstreak > killstreak)
    {
      killstreak = tempkillstreak;
    }
    if (headshot)
    {
      headshots++;
    }
    int i;
    for (i = 0;i < 50 && kills[i][0] != null;i++)
    {
      if (kills[i][0].equals(weapon))
      {
        kills[i][1] = Integer.toString(Integer.parseInt(kills[i][1]) + 1);
        return;
      }
    }
    if (i < 50)
    {
      kills[i][0] = weapon;
      kills[i][1] = "1";
    }
    else
    {
      throw new TooManyWeaponsException("Cod4StatsReader has encountered too many weapons mentioned - Activision must have released new weapons");
    }
  }

  public int getDeaths()
  {
    return deaths;
  }

  public int getSuicides()
  {
    return suicides;
  }

  public int getHeadshots()
  {
    return headshots;
  }

  public String getName()
  {
    return name;
  }

  public String[][] getKills()
  {
    return kills;
  }

  public int getTotalKills()
  {
    int total = 0;
    for (int i = 0;i < 50 && kills[i][1] != null;i++)
    {
      total += Integer.parseInt(kills[i][1]);
    }
    return total;
  }

  public int getKillStreak()
  {
    return killstreak;
  }
}

class TooManyWeaponsException extends Exception
{
  public TooManyWeaponsException(String message)
  {
    super(message);
  }
}