package de.uniluebeck.itm.metadaten.metadatenservice.metadatacollector;



import de.uniluebeck.itm.devicedriver.Device;
import de.uniluebeck.itm.metadatenservice.config.Node;



public class DeviceCollector {
	

  public DeviceCollector () {};
  

     
  public Node devicecollect(Device device, Node node)
  {
//	  device.createGetChipTypeOperation();
	  node.setMicrocontroller("");
	   
	  
	  return node;
	  
  }
}
