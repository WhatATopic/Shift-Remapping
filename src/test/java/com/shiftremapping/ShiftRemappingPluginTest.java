package com.shiftremapping;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class ShiftRemappingPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(ShiftRemappingPlugin.class);
		RuneLite.main(args);
	}
}