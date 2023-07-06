import { Component, OnInit, AfterViewInit, OnDestroy, ViewChild, Input, OnChanges, SimpleChanges } from '@angular/core';
import { SwitchService } from '../../../common/services/switch.service';
import { SwitchidmaskPipe } from "../../../common/pipes/switchidmask.pipe";
import { ToastrService } from 'ngx-toastr';
import { Router } from '@angular/router';
import { DataTableDirective } from 'angular-datatables';
import { Subject ,Subscription} from 'rxjs';
import { NgxSpinnerService } from "ngx-spinner";
import { LoaderService } from "../../../common/services/loader.service";
import { Title } from '@angular/platform-browser';
import { CommonService } from 'src/app/common/services/common.service';

@Component({
  selector: 'app-port-list',
  templateUrl: './port-list.component.html',
  styleUrls: ['./port-list.component.css']
})
export class PortListComponent implements OnInit, AfterViewInit, OnDestroy, OnChanges {
  @ViewChild(DataTableDirective, { static: true })
  dtElement: DataTableDirective;
  @Input() switch = null;
  @Input() loadinterval = false
  isLoaderActive = false;
  dtOptions: any = {};
  dtTrigger: Subject<any> = new Subject();

  currentActivatedRoute : string;
  switch_id: string;	
  switchPortDataSet: any;
  anyData: any;
  portListTimerId: any;  
  portFlowData:any = {};
  portListSubscriber = null;
  portFlowSubscription:Subscription[] = [];
  loadPorts = false;
  switchFilterFlag:string = localStorage.getItem('switchFilterFlag') || 'controller';
  hasStoreSetting ;
  constructor(private switchService:SwitchService,
    private toastr: ToastrService,
    private maskPipe: SwitchidmaskPipe,
    private router:Router,
    private loaderService: LoaderService,
    private titleService: Title,
    private commonService:CommonService,
  ) {
    this.hasStoreSetting = localStorage.getItem('hasSwtStoreSetting') == '1' ? true : false;
  }

  ngOnInit() {
      let ref =this;
    this.switch_id = this.switch;
    this.dtOptions = {
      paging: false,
      retrieve: true,
      autoWidth: false,
      colResize: false,
      dom: 'tpli',
      initComplete:function( settings, json ){
        if(localStorage.getItem('portLoaderEnabled')){
            setTimeout(()=>{ref.loaderService.hide()},2000);
            localStorage.removeItem('portLoaderEnabled');
        }
      },
      "aLengthMenu": [[10, 20, 35, 50, -1], [10, 20, 35, 50, "All"]],
      "aoColumns": [
        { sWidth: '5%' },
        { sWidth: '10%' },
        { sWidth: '10%' },
        { sWidth: '10%' },
        { sWidth: '10%' },
        { sWidth: '13%' },
        { sWidth: '13%' },
        { sWidth: '13%' },
        { sWidth: '13%' },
        { sWidth: '5%' },
        { sWidth: '5%' },
        { sWidth: '5%' },
        { sWidth: '8%' } ],
      language: {
        searchPlaceholder: "Search"
        },
    columnDefs:[
        { targets: [1], visible: this.hasStoreSetting},
        ]
    }
    this.portListData();
  	this.getSwitchPortList()
  }
  
  fulltextSearch(e:any){ 
      var value = e.target.value;
        this.dtElement.dtInstance.then((dtInstance: DataTables.Api) => {
            if(this.hasStoreSetting){
                dtInstance.columns( [1] ).visible( true );
            }else{
                dtInstance.columns( [1] ).visible( false );
            }
            
            dtInstance.search(value)
                  .draw();
        });
  }

  showPortDetail(item){
    var portDataObject = item;
    localStorage.setItem('portDataObject', JSON.stringify(portDataObject));
    this.currentActivatedRoute = 'port-details';
    this.router.navigate(['/switches/details/'+this.switch_id+'/port/'+item.port_number]);
  }

   getSwitchPortList(){
      this.portListTimerId = setInterval(() => {
        if(this.loadinterval){
            this.portListData();
        }
      }, 30000);
  }

  portListData(){
      if(this.loadPorts){
        return ;
      }
      if(localStorage.getItem('portLoaderEnabled')){
          this.loaderService.show("Loading Ports");
      }
      this.loadPorts = true;
      this.portListSubscriber = this.switchService.getSwitchPortsStats(this.maskPipe.transform(this.switch_id,'legacy')).subscribe((data : Array<object>) =>{
        this.rerender();
        this.ngAfterViewInit();
        this.loadPorts= false;
        localStorage.setItem('switchPortDetail', JSON.stringify(data));
        this.switchPortDataSet = data;
        for(let i = 0; i<this.switchPortDataSet.length; i++){
          if(this.switchPortDataSet[i].port_number === '' || this.switchPortDataSet[i].port_number === undefined){
              this.switchPortDataSet[i].port_number = '-';
          }
          if(this.switchPortDataSet[i].interfacetype === '' || this.switchPortDataSet[i].interfacetype === undefined){
              this.switchPortDataSet[i].interfacetype = '-';
          }
          if(typeof(this.switchPortDataSet[i].stats) !=='undefined'){
            if(this.switchPortDataSet[i].stats['tx-bytes'] === '' || this.switchPortDataSet[i].stats['tx-bytes'] === undefined){
                this.switchPortDataSet[i].stats['tx-bytes'] = '-';
            }
            else{
                this.switchPortDataSet[i].stats['tx-bytes'] =  this.commonService.convertBytesToMbps(this.switchPortDataSet[i].stats['tx-bytes']);;
            }
  
            if(this.switchPortDataSet[i].stats['rx-bytes'] === '' || this.switchPortDataSet[i].stats['rx-bytes'] === undefined){
                this.switchPortDataSet[i].stats['rx-bytes'] = '-';
            }
            else{
                this.switchPortDataSet[i].stats['rx-bytes'] =  this.commonService.convertBytesToMbps(this.switchPortDataSet[i].stats['rx-bytes']);
            }
  
            if(this.switchPortDataSet[i].stats['tx-packets'] === '' || this.switchPortDataSet[i].stats['tx-packets'] === undefined){
                this.switchPortDataSet[i].stats['tx-packets']= '-';
            }
  
            if(this.switchPortDataSet[i].stats['rx-packets'] === '' || this.switchPortDataSet[i].stats['rx-packets'] === undefined){
                this.switchPortDataSet[i].stats['rx-packets']= '-';
            }
  
            if(this.switchPortDataSet[i].stats['tx-dropped'] === '' || this.switchPortDataSet[i].stats['tx-dropped'] === undefined){
                this.switchPortDataSet[i].stats['tx-dropped']= '-';
            }
  
            if(this.switchPortDataSet[i].stats['rx-dropped'] === '' || this.switchPortDataSet[i].stats['rx-dropped'] === undefined){
                this.switchPortDataSet[i].stats['rx-dropped']= '-';
            }
  
  
            if(this.switchPortDataSet[i].stats['tx-errors'] === '' || this.switchPortDataSet[i].stats['tx-errors'] === undefined){
                this.switchPortDataSet[i].stats['tx-errors']= '-';
            }
  
            if(this.switchPortDataSet[i].stats['rx-errors'] === '' || this.switchPortDataSet[i].stats['rx-errors'] === undefined){
                this.switchPortDataSet[i].stats['rx-errors']= '-';
            }
  
            if(this.switchPortDataSet[i].stats['collisions'] === '' || this.switchPortDataSet[i].stats['collisions'] === undefined){
                this.switchPortDataSet[i].stats['collisions']= '-';
            }
  
              if(this.switchPortDataSet[i].stats['rx-frame-error'] === '' || this.switchPortDataSet[i].stats['rx-frame-error'] === undefined){
                this.switchPortDataSet[i].stats['rx-frame-error']= '-';
            }
  
            if(this.switchPortDataSet[i].stats['rx-over-error'] === '' || this.switchPortDataSet[i].stats['rx-over-error'] === undefined){
                this.switchPortDataSet[i].stats['rx-over-error']= '-';
            }
  
            if(this.switchPortDataSet[i].stats['rx-crc-error'] === '' || this.switchPortDataSet[i].stats['rx-crc-error'] === undefined){
                this.switchPortDataSet[i].stats['rx-crc-error']= '-';
            }
          }else{
            this.switchPortDataSet[i]['stats'] = {};
          }

          this.fetchPortFlowData(this.switch_id,this.switchPortDataSet[i].port_number);
          
      }

     },error=>{
        //this.toastr.error("No Switch Port data",'Error');
     });
  }

  fetchPortFlowData(switchId,portnumber){
    var swithDetail = localStorage.getItem('switchDetailsJSON') || null;
    var filter = this.switchFilterFlag == 'inventory';
    if(switchId && portnumber!='-'){
         var subscriptionPortFlows =  this.switchService.getSwitchFlows(switchId,filter,portnumber).subscribe(data=>{
          let flowsData:any = data;
          this.portFlowData[portnumber] = {};
          this.portFlowData[portnumber].sumflowbandwidth = 0;
          this.portFlowData[portnumber].noofflows = 0;
            if(flowsData && flowsData.length){
              for(let flow of flowsData){
                this.portFlowData[portnumber].sumflowbandwidth = this.portFlowData[portnumber].sumflowbandwidth + (flow.maximum_bandwidth / 1000);
              }
              this.portFlowData[portnumber].noofflows =flowsData.length;
              if(this.portFlowData[portnumber].sumflowbandwidth){
                this.portFlowData[portnumber].sumflowbandwidth = this.portFlowData[portnumber].sumflowbandwidth.toFixed(3);
              }
              
            }
          },error=>{
            this.portFlowData[portnumber] = {};
           this.portFlowData[portnumber].sumflowbandwidth = 0;
           this.portFlowData[portnumber].noofflows =0;
          }) 
          this.portFlowSubscription.push(subscriptionPortFlows);
    }
  }

   ngAfterViewInit(): void {
       try{
        this.dtTrigger.next();
       }catch(err){

       }   
  }

  ngOnDestroy(): void {
    // Unsubscribe the event
    this.dtTrigger.unsubscribe();
    clearInterval(this.portListTimerId);
    if(this.portListSubscriber){
      this.portListSubscriber.unsubscribe();
      this.portListSubscriber = null;
    }
    if(this.portFlowSubscription && this.portFlowSubscription.length){
      this.portFlowSubscription.forEach((subscription) => subscription.unsubscribe());
      this.portFlowSubscription = [];
    }
    this.loadPorts = false;
  }

  rerender(): void {
      try{
    this.dtElement.dtInstance.then((dtInstance: DataTables.Api) => {
      // Destroy the table first
      try{
        
        dtInstance.destroy();
        this.dtTrigger.next();
      }catch(err){}
    });
    }catch(err){}

    this.initiateInterval();
  }

  initiateInterval(){
    var interval = setInterval(()=>{
        this.dtElement.dtInstance.then((dtInstance: DataTables.Api) => {
            if(this.hasStoreSetting){
                dtInstance.columns( [1] ).visible( true );
            }else{
                dtInstance.columns( [1] ).visible( false );
            }
            clearInterval(interval);
        });
    },1000);
   
  }

  ngOnChanges(change : SimpleChanges){
    if(change.loader){
        if(change.loadinterval.currentValue){
          this.loadinterval  = change.loadinterval.currentValue;
        }
      }
  }
}

