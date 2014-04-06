package appeng.core;

import java.io.File;
import java.util.concurrent.TimeUnit;

import appeng.core.crash.CrashEnhancement;
import appeng.core.crash.CrashInfo;
import appeng.core.features.AEFeature;
import appeng.core.sync.GuiBridge;
import appeng.core.sync.network.NetworkHandler;
import appeng.hooks.TickHandler;
import appeng.integration.IntegrationRegistry;
import appeng.server.AECommand;
import appeng.services.Profiler;
import appeng.services.VersionChecker;
import appeng.util.Platform;

import com.google.common.base.Stopwatch;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerAboutToStartEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppingEvent;
import cpw.mods.fml.common.network.NetworkRegistry;

@Mod(modid = AppEng.modid, name = AppEng.name, version = AEConfig.VERSION, dependencies = AppEng.dependencies)
public class AppEng
{

	private String configPath;

	public String getConfigPath()
	{
		return configPath;
	}

	public final static String modid = "appliedenergistics2";
	public final static String name = "Applied Energistics 2";

	public static AppEng instance;

	public final static String dependencies =

	// a few mods, AE should load after, probably.
	// required-after:AppliedEnergistics2API|all;
	"after:gregtech_addon;after:Mekanism;after:IC2;after:ThermalExpansion;after:BuildCraft|Core;" +

	// depend on version of forge used for build.
			"required-after:Forge@[" // require forge.
			+ net.minecraftforge.common.ForgeVersion.majorVersion + "." // majorVersion
			+ net.minecraftforge.common.ForgeVersion.minorVersion + "." // minorVersion
			+ net.minecraftforge.common.ForgeVersion.revisionVersion + "." // revisionVersion
			+ net.minecraftforge.common.ForgeVersion.buildVersion + ",)"; // buildVersion

	public AppEng() {
		instance = this;

		for (CrashInfo ci : CrashInfo.values())
			FMLCommonHandler.instance().registerCrashCallable( new CrashEnhancement( ci ) );
	}

	public boolean isIntegrationEnabled(String Name)
	{
		return IntegrationRegistry.instance.isEnabled( Name );
	}

	public Object getIntegration(String Name)
	{
		return IntegrationRegistry.instance.getInstance( Name );
	}

	private void startService(String serviceName, Thread thread)
	{
		thread.setName( serviceName );
		thread.setPriority( Thread.MIN_PRIORITY );
		thread.start();
	}

	@EventHandler
	void PreInit(FMLPreInitializationEvent event)
	{
		Stopwatch star = Stopwatch.createStarted();
		configPath = event.getModConfigurationDirectory().getPath() + File.separator + "AppliedEnergistics2" + File.separator;

		AEConfig.instance = new AEConfig( configPath );
		FacadeConfig.instance = new FacadeConfig( configPath );

		AELog.info( "Starting ( PreInit )" );

		if ( Platform.isClient() )
		{
			CreativeTab.init();
			CreativeTabFacade.init();
			CommonHelper.proxy.init();
		}

		Registration.instance.PreInit( event );

		if ( AEConfig.instance.isFeatureEnabled( AEFeature.Profiler ) )
		{
			AELog.info( "Starting Profiler" );
			startService( "AE2 Profiler", (new Thread( Profiler.instance = new Profiler() )) );
		}

		if ( AEConfig.instance.isFeatureEnabled( AEFeature.VersionChecker ) )
		{
			AELog.info( "Starting VersionChecker" );
			startService( "AE2 VersionChecker", new Thread( VersionChecker.instance = new VersionChecker() ) );
		}

		AELog.info( "PreInit ( end " + star.elapsed( TimeUnit.MILLISECONDS ) + "ms )" );
	}

	@EventHandler
	void Init(FMLInitializationEvent event)
	{
		Stopwatch star = Stopwatch.createStarted();
		AELog.info( "Init" );

		Registration.instance.Init( event );
		IntegrationRegistry.instance.init();

		AELog.info( "Init ( end " + star.elapsed( TimeUnit.MILLISECONDS ) + "ms )" );
	}

	@EventHandler
	void PostInit(FMLPostInitializationEvent event)
	{
		Stopwatch star = Stopwatch.createStarted();
		AELog.info( "PostInit" );

		Registration.instance.PostInit( event );
		IntegrationRegistry.instance.postinit();

		AEConfig.instance.save();

		NetworkRegistry.INSTANCE.registerGuiHandler( this, GuiBridge.GUI_Handler );
		NetworkHandler.instance = new NetworkHandler( "AE2" );

		AELog.info( "PostInit ( end " + star.elapsed( TimeUnit.MILLISECONDS ) + "ms )" );
	}

	@EventHandler
	public void serverStopping(FMLServerStoppingEvent event)
	{
		WorldSettings.getInstance().shutdown();
		TickHandler.instance.shutdown();
	}

	@EventHandler
	public void serverStarting(FMLServerAboutToStartEvent evt)
	{
		WorldSettings.getInstance().init();
	}

	@EventHandler
	public void serverStarting(FMLServerStartingEvent evt)
	{
		evt.registerServerCommand( new AECommand( evt.getServer() ) );
	}

}
