package org.usfirst.frc.team1504.robot;

import java.nio.ByteBuffer;

import org.usfirst.frc.team1504.robot.Update_Semaphore.Updatable;

import edu.wpi.first.wpilibj.CANTalon;
import edu.wpi.first.wpilibj.DriverStation;

public class Shooter implements Updatable
{
	private static class Shooter_Task implements Runnable
	{

		private Shooter _task;

		Shooter_Task(Shooter d)
		{
			_task = d;
		}

		public void run()
		{
			_task.shootTask();
		}
	}

	private static Shooter instance = new Shooter();

	private Thread _task_thread;
	private Thread _dump_thread;
	private volatile boolean _thread_alive = true;

	/**
	 * Returns an instance of the shooter; for use in other classes.
	 * 
	 */
	public static Shooter getInstance()
	{
		return Shooter.instance;
	}

	protected Shooter()
	{
		_task_thread = new Thread(new Shooter_Task(this), "1504_Shooter");
		_task_thread.setPriority((Thread.NORM_PRIORITY + Thread.MAX_PRIORITY) / 2);
		_task_thread.start();

		Update_Semaphore.getInstance().register(this);

		ShootInit();

		System.out.println("Red shooter, standing by.");
	}

	public void release()
	{
		_thread_alive = false;
	}

	private DriverStation _ds = DriverStation.getInstance();
	private Logger _logger = Logger.getInstance();
	private CANTalon[] motors = new CANTalon[Map.SHOOTER_MOTOR_PORTS.length];
	private boolean[] _shooter_input;// 0: Intake On, 1: Intake Off, 2: Prep, 3:
										// Launch
	private boolean _prep_on;

	private volatile int _loops_since_last_dump = 0;

	/**
	 * Initializes motors and buttons for usage. Called ONCE.
	 */
	private void ShootInit()
	{
		motors[0] = new CANTalon(Map.INTAKE_TALON_PORT);
		motors[1] = new CANTalon(Map.SHOOTER_LEFT_TALON_PORT);
		motors[2] = new CANTalon(Map.SHOOTER_RIGHT_TALON_PORT);

		_shooter_input = new boolean[Map.SHOOTER_INPUTS.length];
	}

	/**
	 * Updates the array values to the current values of the buttons.
	 */
	public void semaphore_update()
	{
		_shooter_input[0] = IO.intake_on();
		_shooter_input[1] = IO.intake_off();
		_shooter_input[2] = IO.prep();
		_shooter_input[3] = IO.launch();
	}

	// TODO: TEST AND FIND OUT REAL VALUES FOR MOTORS
	/**
	 * Turns on the motor so that the robot can capture a BOULDER.
	 */
	private void intake()
	{
		boolean intake_on = false;

		if (_shooter_input[0] || intake_on)
		{
			intake_on = true;
			motors[0].set(0.7);
		}
		if (_shooter_input[1] || !intake_on)
		{
			intake_on = false;
			motors[0].set(0.0);
		}
	}

	/**
	 * Prepares the BOULDER for launch into the high goal of the CASTLE TOWER.
	 * It first partially jams the BOULDER between the ramp and the intake motor
	 * such that there is no contact with the two launch motors, and then
	 * proceeds to turn those motors on.
	 */
	private void prep()
	{

		if (_shooter_input[2] || _prep_on)
		{
			_prep_on = true;
			motors[0].set(-0.3);

			try
			{
				Thread.sleep(250); // A quarter of a second.
			} catch (InterruptedException ex)
			{
				Thread.currentThread().interrupt();
			}

			motors[0].set(0.0);
			motors[1].set(1.0);
			motors[2].set(1.0);
		}
	}

	/**
	 * Launches the BOULDER.
	 */
	private void launch()
	{

		if (_shooter_input[3])
		{
			motors[0].set(1.0);
			try
			{
				Thread.sleep(333); // Almost a third of a second
			} catch (InterruptedException ex)
			{
				Thread.currentThread().interrupt();
			}
			_prep_on = false;
			motors[1].set(0);
			motors[2].set(0);
			motors[0].set(0);
		}
	}

	// All motors should have: Bus Voltage, Output Current, and Set Point
	/**
	 * Creates an array of data to log.
	 */
	private void dump()
	{
		byte[] output = new byte[12 + 1 + 4 + 4];

		int loops_since_last_dump = _loops_since_last_dump;

		for (int i = 0; i < motors.length; i++)
		{
			int j = i * 4;// in order to not have nested loops, array indices
							// get calculated based on i
			output[j] = Utils.double_to_byte(motors[i].get());
			output[j + 1] = Utils.double_to_byte(motors[i].getSetpoint());
			output[j + 2] = Utils.double_to_byte(motors[i].getBusVoltage());
			output[j + 3] = Utils.double_to_byte(motors[i].getOutputCurrent());
		}
		// current state of buttons, crushed into one byte , with the
		// button array: 0: Intake On, 1: Intake Off, 2: Prep, 3: Launch
		byte buttons = 0;
		if (_shooter_input[0])
		{
			buttons += 8;
		}
		if (_shooter_input[1])
		{
			buttons += 4;
		}
		if (_shooter_input[2])
		{
			buttons += 2;
		}
		if (_shooter_input[3])
		{
			buttons += 1;
		}

		output[12] = buttons;

		ByteBuffer.wrap(output, 12, 4).putInt(loops_since_last_dump);
		ByteBuffer.wrap(output, 16, 4).putInt((int) System.currentTimeMillis());

		if (_logger != null)
		{
			if (_logger.log(Map.LOGGED_CLASSES.SHOOTER, output))
				_loops_since_last_dump -= loops_since_last_dump;
		}
	}

	/**
	 * Controls the above methods.
	 */
	private void shootTask()
	{
		boolean logpls = false;
		while (_thread_alive)
		{
			if (_ds.isEnabled() && _ds.isOperatorControl())
			{
				intake();
				prep();
				launch();
				logpls = true;
			}
			if (logpls)
			{
				// Dump is done in its own thread, for speed.
				if (_dump_thread == null || !_dump_thread.isAlive())
				{
					_dump_thread = new Thread(new Runnable()
					{
						public void run()
						{
							dump();
						}
					});
					_dump_thread.start();
				}
				logpls = false;
			}
		}
	}
}
