/*
 * Copyright (c) 2018, Adam <Adam@sigterm.info>
 * Copyright (c) 2018, Abexlry <abexlry@gmail.com>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.shiftremapping;

import com.google.inject.Provides;
import java.awt.Color;
import javax.inject.Inject;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.ScriptCallbackEvent;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.JagexColors;
import net.runelite.client.util.ColorUtil;

@PluginDescriptor(
	name = "Shift Remapping",
	description = "Allows remapping of shift key since it never got added to key remapping.",
	tags = {"shift", "key", "remapping", "remap", "bind"}
)
public class ShiftRemappingPlugin extends Plugin
{
	private static final String PRESS_ENTER_TO_CHAT = "Press Enter to Chat...";

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private KeyManager keyManager;

	@Inject
	private ShiftRemappingConfig config;

	@Inject
	private ShiftRemappingListener inputListener;

	@Getter(AccessLevel.PACKAGE)
	@Setter(AccessLevel.PACKAGE)
	private boolean typing;

	@Override
	protected void startUp() throws Exception
	{
		typing = !config.enterToChat();
		keyManager.registerKeyListener(inputListener);

		clientThread.invoke(() ->
		{
			if (client.getGameState() == GameState.LOGGED_IN
				&& config.enterToChat())
			{
				lockChat();
				// Clear any typed text
				client.setVarcStrValue(VarClientID.CHATINPUT, "");
			}
		});
	}

	@Override
	protected void shutDown() throws Exception
	{
		clientThread.invoke(() ->
		{
			if (client.getGameState() == GameState.LOGGED_IN)
			{
				unlockChat();
			}
		});

		keyManager.unregisterKeyListener(inputListener);
	}

	@Provides
    ShiftRemappingConfig getConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ShiftRemappingConfig.class);
	}

	boolean chatboxFocused()
	{
		Widget chatboxParent = client.getWidget(InterfaceID.Chatbox.UNIVERSE);
		if (chatboxParent == null || chatboxParent.getOnKeyListener() == null)
		{
			return false;
		}

		// the search box on the world map can be focused, and chat input goes there, even
		// though the chatbox still has its key listener.

		Widget worldMapSearch = client.getWidget(InterfaceID.Worldmap.MAPLIST_DISPLAY);
		if (worldMapSearch != null && client.getVarcIntValue(VarClientID.WORLDMAP_SEARCHING) == 1)
		{
			return false;
		}

		//Included check to make sure options chat menu isn't open and make sure bank pin menu isn't open
        return !config.disableIfMenuOpen() || (!isOptionsDialogOpen() && isHidden(InterfaceID.BANKPIN_KEYPAD) && !isDialogOpen());
    }

	boolean isDialogOpen()
	{
		// Most chat dialogs with numerical input are added without the chatbox or its key listener being removed,
		// so chatboxFocused() is true. The chatbox onkey script uses the following logic to ignore key presses,
		// so we will use it too to not remap F-keys.
		return isHidden(InterfaceID.Chatbox.MES_LAYER_HIDE) || isHidden(InterfaceID.Chatbox.CHATDISPLAY)
				// We want to block F-key remapping in the bank pin interface too, so it does not interfere with the
				// Keyboard Bankpin feature of the Bank plugin
				|| !isHidden(InterfaceID.BankpinKeypad.UNIVERSE);
	}

	boolean isOptionsDialogOpen()
	{
		return client.getWidget(InterfaceID.Chatmenu.OPTIONS) != null;
	}

	private boolean isHidden(int component)
	{
		Widget w = client.getWidget(component);
		return w == null || w.isSelfHidden();
	}

	@Subscribe
	public void onScriptCallbackEvent(ScriptCallbackEvent scriptCallbackEvent)
	{
		switch (scriptCallbackEvent.getEventName())
		{
			case "setChatboxInput":
				Widget chatboxInput = client.getWidget(InterfaceID.Chatbox.INPUT);
				if (chatboxInput != null && !typing && config.enterToChat())
				{
					setChatboxWidgetInput(chatboxInput, PRESS_ENTER_TO_CHAT);
				}
				break;
			case "blockChatInput":
				if (!typing && config.enterToChat())
				{
					int[] intStack = client.getIntStack();
					int intStackSize = client.getIntStackSize();
					intStack[intStackSize - 1] = 1;
				}
				break;
		}
	}

	void lockChat()
	{
		Widget chatboxInput = client.getWidget(InterfaceID.Chatbox.INPUT);
		if (chatboxInput != null && config.enterToChat())
		{
			setChatboxWidgetInput(chatboxInput, PRESS_ENTER_TO_CHAT);
		}
	}

	void unlockChat()
	{
		Widget chatboxInput = client.getWidget(InterfaceID.Chatbox.INPUT);
		if (chatboxInput != null)
		{
			if (client.getGameState() == GameState.LOGGED_IN)
			{
				final boolean isChatboxTransparent = client.isResized() && client.getVarbitValue(VarbitID.CHATBOX_TRANSPARENCY) == 1;
				final Color textColor = isChatboxTransparent ? JagexColors.CHAT_TYPED_TEXT_TRANSPARENT_BACKGROUND : JagexColors.CHAT_TYPED_TEXT_OPAQUE_BACKGROUND;
				setChatboxWidgetInput(chatboxInput, ColorUtil.wrapWithColorTag(client.getVarcStrValue(VarClientID.CHATINPUT) + "*", textColor));
			}
		}
	}

	private void setChatboxWidgetInput(Widget widget, String input)
	{
		String text = widget.getText();
		int idx = text.indexOf(':');
		if (idx != -1)
		{
			String newText = text.substring(0, idx) + ": " + input;
			widget.setText(newText);
		}
	}
}
