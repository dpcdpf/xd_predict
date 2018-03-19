package com.elasticcloudservice.predict;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

public class Predict {
    
	public static String[] predictVm(String[] ecsContent, String[] inputContent)  {

		/** =========do your work here========== **/

		String[] results = new String[ecsContent.length*15];

		List<String> history = new ArrayList<String>();
        /*
         * 对于没有虚拟机存在的天视为忽略 后续需要对某种虚拟机出现异常波动的给于去除/修正
         * @param flavorAll 虚拟机的历史数据 Map存储  key:日期 value:每天的各种虚拟机类型数量 flavorDay
         * @param flavorDay 每天的各种虚拟机类型及数量 key:虚拟机名称 value:虚拟机数量
         */
		Map<String,TreeMap<String,Integer>> flavorAll=readHisFlavor(ecsContent);
		/*
		 * 读取处理预测数据
		 * @param machineCPU 物理机CPU核数
		 * @param machineMemory 物理机内存大小 GB
		 * @param nPridictFlavor 预测的虚拟机数量
		 * @param preditFla 预测虚拟机的详细信息  虚拟机名字 虚拟机所需核数 内存(MB)
		 * @param pridictDay 预测的天数
		 * @param optimize 优化目标  CPU/Memory
		 */
		String[] machine=inputContent[0].split(" ");
		int machineCPU=Integer.parseInt(machine[0]);
		int machineMemory=Integer.parseInt(machine[1])*1024;
		int nPridictFlavor=Integer.parseInt(inputContent[2]);
		List<Map<String,List<Integer>>> pridictData=readPrictData(inputContent);
		Map<String,List<Integer>> preditFla=pridictData.get(0);
		int pridictDay=0;
		String optimize = null;
		Map<String,List<Integer>> preditOtherData=pridictData.get(1);// 只有一个数据
		for(Map.Entry<String,List<Integer>>entry:preditOtherData.entrySet()) {
			optimize=entry.getKey();
			pridictDay=entry.getValue().get(0);
		}
		//根据历史数据虚拟机按周进行排序统计 倒序统计 不足一周舍弃 正序排列
		Map<String,List<Integer>> sevenDatasAll=countBasedSevenDay(flavorAll,preditFla);
		//预测虚拟机数据 一元线性回归
		Map<String,Integer> predict=preFutureFla(sevenDatasAll,preditFla,pridictDay);
		//分配虚拟机  优化目标CPU 则内存不能超
		//排序+最佳适合递减算法
		//将预测虚拟机按照优化目标进行排序
		List<Map.Entry<String, List<Integer>>> sortPreditFlaList=sortPreditFlavorBasedOptimize(preditFla,optimize);
		//按照顺序进行 最大优化目标的先排
		Map<Integer,Map<String,Integer>> machineDividFla=dividMachine(predict,sortPreditFlaList,nPridictFlavor,optimize,machineCPU,machineMemory);
		//输出预测结果
		int sumPrictFla=0;
		for(Map.Entry<String, List<Integer>> entry:preditFla.entrySet()) {
			sumPrictFla+=predict.get(entry.getKey());
		}
		//添加虚拟机总数及各类型数目
		history.add(String.valueOf(sumPrictFla));
		for(Map.Entry<String, List<Integer>> entry:preditFla.entrySet()) {
			history.add(entry.getKey()+" "+ predict.get(entry.getKey()));
		}
		//添加空行间隔
		history.add(" ");
		//添加物理机总数及分配信息
		history.add(String.valueOf(machineDividFla.size()));
		for(Map.Entry<Integer,Map<String,Integer>> 	machineEntry:machineDividFla.entrySet()) {
			StringBuffer macfla=new StringBuffer();
			macfla.append(machineEntry.getKey()+" ");
			Map<String,Integer> dividfla=machineEntry.getValue();
			for(Map.Entry<String, List<Integer>> entry:preditFla.entrySet()) {
				Integer number=dividfla.get(entry.getKey());
				if(number!=null) {
					macfla.append(entry.getKey()+" "+number+" ");
				}
			}
			String line=String.valueOf(macfla);
			history.add(line);
		}
		for (int i = 0; i < history.size(); i++) {
			results[i] = history.get(i);
		}

		return results;
	}
	/*
	 * 将预测出的虚拟机分配到机器 根据优化目标的不同 两种分法
	 * @param predict 预测的虚拟机类型及数量 key:虚拟机名称 value:虚拟机数量
	 * @param sortPreditFlaList 根据优化目标大小降序排列的虚拟机类型数据 key:虚拟机名称 value:虚拟机cpu数 内存数
	 * @param nPridictFlavor 需要预测的虚拟机种类数
	 * @param optimize 优化目标 CPU/MEM
	 * @param machineCPU 物理机 CPU核数
	 * @param machineMemory 物理机内存数 MB
	 * @return machineDividFla 虚拟机到物理机的分配结果 key:物理机编号 value:对应编号下分配的虚拟机类型及数量
	 */
	private static Map<Integer,Map<String,Integer>> dividMachine(Map<String, Integer> predict,
			List<Entry<String, List<Integer>>> sortPreditFlaList, int nPridictFlavor,String optimize,int machineCPU,int machineMemory ) {
		// TODO Auto-generated method stub
		Map<Integer,Map<String,Integer>>machineDividFla=new TreeMap<>();
		Map<Integer,List<Integer>>machineCpuMemory=new TreeMap<>();
		int numMachine=1;//物理机编号及计数
		if(optimize.equals("CPU")) {
			//考虑优化CPU CPU填充要最大 内存不能超
			machineDividFla=dividedBasedCpu(predict,sortPreditFlaList,nPridictFlavor,optimize, machineCPU,machineMemory);
		}
		else {
			//考虑优化MEM内存 内存填充要最大 cpu数不能超
			machineDividFla=dividedBasedMemory(predict,sortPreditFlaList,nPridictFlavor,optimize, machineCPU,machineMemory);
		}
		return machineDividFla;
	}
	/*
	 * 将预测的虚拟机根据虚拟机的优化目标进行降序排列，先分配优化目标大的虚拟机到物理机再依次分配小的
	 * 如果当前需要分配的虚拟机不能分配到物理机，则新建一个物理机放置
	 * 如果物理机数量多于一个 则遍历物理机找到能装填当前虚拟机的物理机集(即物理机装填后cpu和memory不能超过物理机)
	 * 将能装填当前虚拟机的物理机集根据装填优化目标装填率最大进行排列 第一项即是装填后装填率最大的 即成为当前虚拟机的装填物理机
	 * @param predict 预测的虚拟机类型及数量 key:虚拟机名称 value:虚拟机数量
	 * @param sortPreditFlaList 根据优化目标大小降序排列的虚拟机类型数据 key:虚拟机名称 value:虚拟机cpu数 内存数
	 * @param nPridictFlavor 需要预测的虚拟机种类数
	 * @param optimize 优化目标 CPU/MEM
	 * @param machineCPU 物理机 CPU核数
	 * @param machineMemory 物理机内存数 MB
	 * @return machineDividFla 虚拟机到物理机的分配结果 key:物理机编号 value:对应编号下分配的虚拟机类型及数量
	 */
	private static Map<Integer, Map<String, Integer>> dividedBasedMemory(Map<String, Integer> predict,
			List<Entry<String, List<Integer>>> sortPreditFlaList, int nPridictFlavor, String optimize, int machineCPU,
			int machineMemory) {
		// TODO Auto-generated method stub
		Map<Integer,Map<String,Integer>>machineDividFla=new TreeMap<>();
		Map<Integer,List<Integer>>machineCpuMemory=new TreeMap<>();
		for(int i=0;i<nPridictFlavor;i++) {
			String flaName=sortPreditFlaList.get(i).getKey();
			int singlecpu=sortPreditFlaList.get(i).getValue().get(0);//优化目标 虚拟机的值   一般性CPU
			int singlememo=sortPreditFlaList.get(i).getValue().get(1);//非优化目标虚拟机的值  一般性内存 Memory MB注意单位换算
			int numprefla=predict.get(flaName);
			//从优化目标大的开始遍历装填 遍历完当前最大优化目标的虚拟机数量
			for(int k=0;k<numprefla;k++){
				//遍历找到最大适合的物理机
				if(machineDividFla.size()<=1) {//物理机为1或者0直接填
					//判断内存及CPU
					int filledcpu=machineCpuMemory.get(1)==null?0:machineCpuMemory.get(1).get(0);
					int filledMemory=machineCpuMemory.get(1)==null?0:machineCpuMemory.get(1).get(1);
					if(((filledcpu+singlecpu)<=machineCPU)&&((filledMemory+singlememo)<=machineMemory)) {
						//直接装填一号物理机  一号是空的直接新建map
						Map<String,Integer> oneMachine=machineDividFla.get(1)==null?new TreeMap<String,Integer>():machineDividFla.get(1);
						//判断装填此类型的虚拟机数目
						Integer num=oneMachine.get(flaName);
						if(num==null) {
							oneMachine.put(flaName, 1);
						}
						else {
							oneMachine.put(flaName, ++num);
						}
						//添加此虚拟机
						machineDividFla.put(1, new TreeMap<String,Integer>(oneMachine));
						machineCpuMemory.put(1,Arrays.asList(filledcpu+singlecpu,filledMemory+singlememo));
					        }
					else {
						//一号装填不下 新建物理机 直接装填
						int newMachineNum=machineDividFla.size()+1;
						Map<String,Integer> oneMachine=new TreeMap<String,Integer>();
						oneMachine.put(flaName, 1);
						machineDividFla.put(newMachineNum, new TreeMap<String,Integer>(oneMachine));
						machineCpuMemory.put(newMachineNum,Arrays.asList(singlecpu,singlememo));
					    }
				}
					//如果物理机个数大于1 遍历找到能装 将填充率最大的
				else if(machineDividFla.size()>1) {
						//1 找到可用的  2有可用的 根据填充率排序填充 找不到可用的 新建一个物理机
					   List<Integer> capableMacNum=new ArrayList<>();
					   //找到可用的
					   for(Map.Entry<Integer, List<Integer>> entry:machineCpuMemory.entrySet()) {
						   if(((entry.getValue().get(0)+singlecpu)<=machineCPU)&&((entry.getValue().get(1)+singlememo)<=machineMemory)) {
							   capableMacNum.add(entry.getKey());
						   }
					   }
						   //判断有无可用的
						   if(capableMacNum.size()==0) {
							   //新建物理机
							   int newMachineNum=machineDividFla.size()+1;
								Map<String,Integer> oneMachine=new TreeMap<String,Integer>();
								oneMachine.put(flaName, 1);
								machineDividFla.put(newMachineNum, new TreeMap<String,Integer>(oneMachine));
								machineCpuMemory.put(newMachineNum,Arrays.asList(singlecpu,singlememo));
						   }
						   else {
							   //有可用的 根据填充率排序
							   Collections.sort(capableMacNum, new Comparator<Integer>() {

								@Override
								public int compare(Integer o1, Integer o2) {
									// TODO Auto-generated method stub
									int flag=0;
									//以填充CPU满率为排序 降序
									if(machineCpuMemory.get(o1).get(1)>machineCpuMemory.get(o2).get(1)) {
										flag=-1;
									}
									if(machineCpuMemory.get(o1).get(1)<machineCpuMemory.get(o2).get(1)) {
										flag=1;
									}
									return flag;
								}
								   
							   });
							   //装填填充率最大的那个
							   int maxMachine=capableMacNum.get(0);
							   int filledcpu=machineCpuMemory.get(maxMachine).get(0);
							   int filledMemory=machineCpuMemory.get(maxMachine).get(1);
							   //获得最大的物理机
							   Map<String,Integer> oneMachine=machineDividFla.get(maxMachine);
								//判断装填此类型的虚拟机数目
								Integer num=oneMachine.get(flaName);
								if(num==null) {
									oneMachine.put(flaName, 1);
								}
								else {
									oneMachine.put(flaName, ++num);
								}
								//添加此虚拟机
								machineDividFla.put(maxMachine, new TreeMap<String,Integer>(oneMachine));
								machineCpuMemory.put(maxMachine,Arrays.asList(filledcpu+singlecpu,filledMemory+singlememo));
						   }
				
	         }
			}
		}
		return machineDividFla;
	}
	/*
	 * 将预测的虚拟机根据虚拟机的优化目标进行降序排列，先分配优化目标大的虚拟机到物理机再依次分配小的
	 * 如果当前需要分配的虚拟机不能分配到物理机，则新建一个物理机放置
	 * 如果物理机数量多于一个 则遍历物理机找到能装填当前虚拟机的物理机集(即物理机装填后cpu和memory不能超过物理机)
	 * 将能装填当前虚拟机的物理机集根据装填优化目标装填率最大进行排列 第一项即是装填后装填率最大的 即成为当前虚拟机的装填物理机
	 * @param predict 预测的虚拟机类型及数量 key:虚拟机名称 value:虚拟机数量
	 * @param sortPreditFlaList 根据优化目标大小降序排列的虚拟机类型数据 key:虚拟机名称 value:虚拟机cpu数 内存数
	 * @param nPridictFlavor 需要预测的虚拟机种类数
	 * @param optimize 优化目标 CPU/MEM
	 * @param machineCPU 物理机 CPU核数
	 * @param machineMemory 物理机内存数 MB
	 * @return machineDividFla 虚拟机到物理机的分配结果 key:物理机编号 value:对应编号下分配的虚拟机类型及数量
	 */
	private static Map<Integer, Map<String, Integer>> dividedBasedCpu(Map<String, Integer> predict,
			List<Entry<String, List<Integer>>> sortPreditFlaList, int nPridictFlavor, String optimize, int machineCPU,
			int machineMemory) {
		// TODO Auto-generated method stub
		Map<Integer,Map<String,Integer>>machineDividFla=new TreeMap<>();
		Map<Integer,List<Integer>>machineCpuMemory=new TreeMap<>();
		for(int i=0;i<nPridictFlavor;i++) {
			String flaName=sortPreditFlaList.get(i).getKey();
			int singlecpu=sortPreditFlaList.get(i).getValue().get(0);//优化目标 虚拟机的值   一般性CPU
			int singlememo=sortPreditFlaList.get(i).getValue().get(1);//非优化目标虚拟机的值  一般性内存 Memory MB注意单位换算
			int numprefla=predict.get(flaName);
			//从优化目标大的开始遍历装填 遍历完当前最大优化目标的虚拟机数量
			for(int k=0;k<numprefla;k++){
				//遍历找到最大适合的物理机
				if(machineDividFla.size()<=1) {//物理机为1或者0直接填
					//判断内存及CPU
					int filledcpu=machineCpuMemory.get(1)==null?0:machineCpuMemory.get(1).get(0);
					int filledMemory=machineCpuMemory.get(1)==null?0:machineCpuMemory.get(1).get(1);
					if(((filledcpu+singlecpu)<=machineCPU)&&((filledMemory+singlememo)<=machineMemory)) {
						//直接装填一号物理机  一号是空的直接新建map
						Map<String,Integer> oneMachine=machineDividFla.get(1)==null?new TreeMap<String,Integer>():machineDividFla.get(1);
						//判断装填此类型的虚拟机数目
						Integer num=oneMachine.get(flaName);
						if(num==null) {
							oneMachine.put(flaName, 1);
						}
						else {
							oneMachine.put(flaName, ++num);
						}
						//添加此虚拟机
						machineDividFla.put(1, new TreeMap<String,Integer>(oneMachine));
						machineCpuMemory.put(1,Arrays.asList(filledcpu+singlecpu,filledMemory+singlememo));
					        }
					else {
						//一号装填不下 新建物理机 直接装填
						int newMachineNum=machineDividFla.size()+1;
						Map<String,Integer> oneMachine=new TreeMap<String,Integer>();
						oneMachine.put(flaName, 1);
						machineDividFla.put(newMachineNum, new TreeMap<String,Integer>(oneMachine));
						machineCpuMemory.put(newMachineNum,Arrays.asList(singlecpu,singlememo));
					    }
				}
					//如果物理机个数大于1 遍历找到能装 将填充率最大的
				else if(machineDividFla.size()>1) {
						//1 找到可用的  2有可用的 根据填充率排序填充 找不到可用的 新建一个物理机
					   List<Integer> capableMacNum=new ArrayList<>();
					   //找到可用的
					   for(Map.Entry<Integer, List<Integer>> entry:machineCpuMemory.entrySet()) {
						   if(((entry.getValue().get(0)+singlecpu)<=machineCPU)&&((entry.getValue().get(1)+singlememo)<=machineMemory)) {
							   capableMacNum.add(entry.getKey());
						   }
					   }
						   //判断有无可用的
						   if(capableMacNum.size()==0) {
							   //新建物理机
							   int newMachineNum=machineDividFla.size()+1;
								Map<String,Integer> oneMachine=new TreeMap<String,Integer>();
								oneMachine.put(flaName, 1);
								machineDividFla.put(newMachineNum, new TreeMap<String,Integer>(oneMachine));
								machineCpuMemory.put(newMachineNum,Arrays.asList(singlecpu,singlememo));
						   }
						   else {
							   //有可用的 根据填充率排序
							   Collections.sort(capableMacNum, new Comparator<Integer>() {

								@Override
								public int compare(Integer o1, Integer o2) {
									// TODO Auto-generated method stub
									int flag=0;
									//以填充CPU满率为排序 降序
									if(machineCpuMemory.get(o1).get(0)>machineCpuMemory.get(o2).get(0)) {
										flag=-1;
									}
									if(machineCpuMemory.get(o1).get(0)<machineCpuMemory.get(o2).get(0)) {
										flag=1;
									}
									return flag;
								}
								   
							   });
							   //装填填充率最大的那个
							   int maxMachine=capableMacNum.get(0);
							   int filledcpu=machineCpuMemory.get(maxMachine).get(0);
							   int filledMemory=machineCpuMemory.get(maxMachine).get(1);
							   //获得最大的物理机
							   Map<String,Integer> oneMachine=machineDividFla.get(maxMachine);
								//判断装填此类型的虚拟机数目
								Integer num=oneMachine.get(flaName);
								if(num==null) {
									oneMachine.put(flaName, 1);
								}
								else {
									oneMachine.put(flaName, ++num);
								}
								//添加此虚拟机
								machineDividFla.put(maxMachine, new TreeMap<String,Integer>(oneMachine));
								machineCpuMemory.put(maxMachine,Arrays.asList(filledcpu+singlecpu,filledMemory+singlememo));
						   }
				
	         }
			}
		}
		return machineDividFla;
	}
	
	/*
	 * 将预测的虚拟机按照优化目标进行排序 降序 即优化目标大的放在前面 
	 * @param preditFla 给定的预测的虚拟机数据  key:虚拟机名称 value:虚拟机预测数量
	 * @param optimize 优化目标 CPU/Memory
	 * @return sortPreditFlaList 按优化目标排序好的预测虚拟机数据 从下标往后依次优化目标减小排列
	 */
	private static List<Entry<String, List<Integer>>> sortPreditFlavorBasedOptimize(
			Map<String, List<Integer>> preditFla, String optimize) {
		// TODO Auto-generated method stub
		List<Map.Entry<String, List<Integer>>> sortPreditFlaList=new ArrayList<Map.Entry<String, List<Integer>>>();
		for(Map.Entry<String, List<Integer>> preEntry:preditFla.entrySet()) {
			sortPreditFlaList.add(preEntry);
		}
		final int indexOp=optimize.equals("CPU")?0:1;
		final int indexNop=optimize.equals("CPU")?1:0;
		//按优化目标逆序排列
		Collections.sort(sortPreditFlaList, new Comparator<Map.Entry<String,  List<Integer>>>(){

			@Override
			public int compare(Entry<String, List<Integer>> o1, Entry<String, List<Integer>> o2) {
				// TODO Auto-generated method stub
				int flag=0;
		        if(o1.getValue().get(indexOp)>o2.getValue().get(indexOp)) {
		        	flag=-1;
		        }
		        if(o1.getValue().get(indexOp)<o2.getValue().get(indexOp)) {
		        	flag=1;
		        }
				return flag;
			}
		});
		return sortPreditFlaList;
	}
	/*
	 * 预测虚拟机数据 一元线性回归 x:第一周 第二周 ....y：按周进行统计的虚拟机数量 
	 * 对于将来的也是分为周为单位进行预测  假设1-2周
	 * @param sevenDatasAll以周为单位倒序统计 正序排列的虚拟机数据  key:虚拟机名称 value:虚拟机按周进行统计的数据 根据日期从前往后
	 * @param preditFla 题目给出的需要预测的虚拟机数据 key:虚拟机名称 value:虚拟机cpu及memory
	 * @return predict 预测出来的指定类型的虚拟机数目  key:虚拟机名称 value:预测的虚拟机数量
	 */
	private static Map<String, Integer> preFutureFla(Map<String, List<Integer>> sevenDatasAll,
			Map<String, List<Integer>> preditFla,int pridictDay) {
		// TODO Auto-generated method stub
		Map< String,Integer> predict=new TreeMap<>();
		for(Map.Entry<String, List<Integer>> preEntry:preditFla.entrySet()) {
			String preflaName=preEntry.getKey();//预测的虚拟机
			List<Integer> historynumbers=sevenDatasAll.get(preflaName);
			//求系数
			int n=historynumbers.size();
			Integer[] xx=new Integer[n];
			Integer[] yy=new Integer[n];
			for(int i=0;i<n;i++) {
				xx[i]=i+1;
			}
			for(int i=0;i<n;i++) {
				yy[i]=historynumbers.get(i);
			}
			LeastSquareMethod eastSquareMethod = new LeastSquareMethod(xx,yy,3);
			int period=1;
			int preTime=pridictDay/period;
			//一周或两周预测
			int preNumber =0;
			for(int i=1;i<=preTime;i++) {
				preNumber+=Math.round(eastSquareMethod.fit(n+i));
				if(preNumber<0) {
					preNumber=0;
				}
			}
			/*
			 * 线性预测先注释
			 */
			/*
			double a=0;
			double b=0;
			int sumxiyi=0;
			int sumxi=0;
			int sumyi=0;
			int sumxixi=0;
			for(int i=0;i<n;i++) {
				sumxiyi+=(i+1)*historynumbers.get(i);
				sumxi+=(i+1);
				sumyi+=historynumbers.get(i);
				sumxixi+=(i+1)*(i+1);
			}
			b=(n*sumxiyi-sumxi*sumyi)*1.0/(n*sumxixi-sumxi*sumxi);
			a=sumyi*1.0/n-b*sumxi*1.0/n;
			int preTime=pridictDay/7;
			//一周或两周预测
			int preNumber =0;
			for(int i=1;i<=preTime;i++) {
				preNumber+=(int)Math.round(a+b*(n+i));
				if(preNumber<0) {
					preNumber=0;
				}
			}
			*/
			predict.put(preflaName, preNumber);
		}
		return  predict;
	}
	/*
	 * 虚拟机按周进行排序统计 倒序统计 不足一周舍弃 正序排列
	 * @param flavorAll 历史虚拟机数据
	 * @param preditFla 题目需要预测的虚拟机数据  key:虚拟机名称 value:cpu及memory
	 * @return sevenDatasAll 按照七天为单位进行统计的数据 时间从最后一天往前统计  七天的数据合成一个 最后的不足一周的数据舍弃 key:虚拟机名称 value:虚拟机统计数据list 时间从前往后
	 * 后续完善统计时去除异常
	 */
	private static Map<String, List<Integer>> countBasedSevenDay(Map<String, TreeMap<String, Integer>> flavorAll,Map<String,List<Integer>> preditFla) {
		// TODO Auto-generated method stub
		List<Map.Entry<String, TreeMap<String,Integer>>> flavorAllList=new ArrayList<>();
		for(Map.Entry<String, TreeMap<String,Integer>> entry:flavorAll.entrySet()) {
			flavorAllList.add(entry);
		}
		int period=1;//尝试一天的统计
		//@param sevenDatas按一周为单位统计  暂时没有去除异常数据
		Map<String,List<Integer>> sevenDatasAll=new TreeMap<>();
		int count=0;
		int sum=0;
		for(Map.Entry<String, List<Integer>> preEntry:preditFla.entrySet()) {
			String fla=preEntry.getKey();//预测的虚拟机
		    List<Integer> sevenDatas=new ArrayList<>();
		//倒序统计
		for(int i=flavorAllList.size()-1;i>=0;i--) {
			Integer number=flavorAllList.get(i).getValue().get(fla);
			if(number==null) {
				number=0;
			}
			sum+=number;
			count++;
			if(count%period==0) {
				sevenDatas.add(0,sum);
				sum=0;
			}
		}
		sevenDatasAll.put(fla,sevenDatas);
		count=0;
		sum=0;
		}
		return sevenDatasAll;
	}
    /*
     * @param preditFla 预测虚拟机的详细信息  虚拟机名字 虚拟机所需核数 内存(MB)
	 * @param pridictDay 预测的天数
	 * @param optimize 优化目标  CPU/Memory
	 * @return pre 题目需要预测的虚拟机数据
     */
	private static List<Map<String, List<Integer>>> readPrictData(String[] inputContent) {
		// TODO Auto-generated method stub
		List<Map<String, List<Integer>>> pre=new ArrayList<>();
		Map<String,List<Integer>> preditFla=new TreeMap<>();
		Map<String,List<Integer>> predictOtherData=new TreeMap<>();
		String optimize =null;
		int pridictDay=0;
		int row=3;
		for(;;row++) {
			if(inputContent[row].equals("")) {
				break;
			}
			String[] str=inputContent[row].split(" ");
			preditFla.put(str[0], Arrays.asList(Integer.parseInt(str[1]),Integer.parseInt(str[2])));
		}
		optimize=inputContent[++row];
		DateFormat timeformat= new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"); 
		++row;//
	    String times1=inputContent[++row];
	    
	    String times2=inputContent[++row];
	    Date dates=null;
		try {
			dates = timeformat.parse(times1);
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			System.out.println("datas wrong ");
		}
	    Date dates2=null;
		try {
			dates2 = timeformat.parse(times2);
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			System.out.println("datas2 wrong ");
		}
	   
	    long adf=dates2.getTime()-dates.getTime();
	    pridictDay=(int)adf/86400000;
	    predictOtherData.put(optimize, Arrays.asList(pridictDay));
	    pre.add(preditFla);
	    pre.add(predictOtherData);
		return pre;
	}
	/*
     * 对于没有虚拟机存在的天视为忽略 后续需要对某种虚拟机出现异常波动的给于去除/修正
     * @param flavorAll 虚拟机的历史数据 Map存储  key:日期 value:每天的各种虚拟机数量
     * @param flavorDay 每天的各种虚拟机类型及数量 key:虚拟机名称 value:虚拟机数量】
     * @param dataMark 日期的记录 更新日期
     */
	private static Map<String, TreeMap<String, Integer>> readHisFlavor(String[] ecsContent) {
		// TODO Auto-generated method stub
		String line;
        Map<String,TreeMap<String,Integer>> flavorAll=new TreeMap<String,TreeMap<String,Integer>>();
        TreeMap<String,Integer> flavorDay=new TreeMap<>();
        String dataMark=null;
		for (int i=0; i < ecsContent.length; i++) {
			line=ecsContent[i];//
			String[] str=ecsContent[i].split("\\t");//
			if ((line=ecsContent[i]).contains(" ")
					&& ecsContent[i].split("\\t").length == 3) {
                //56498ccb-a969	flavor15	2015-01-15 00:37:06
				//56498ccc-85e6	flavor12	2015-01-15 00:38:56
				//对于没有虚拟机存在的天视为忽略 后续对某种虚拟机出现异常波动的给于去除/修正
				String[] array = ecsContent[i].split("\\t");
				String uuid = array[0];
				String flavorName = array[1];
				String createTime = array[2];
                String[] dataTime=createTime.split(" ");
                String today=dataTime[0];
                if(dataMark==null) {
                	dataMark=today;
                }
                if(dataMark.equals(today)) {
                	 Integer nflavor=flavorDay.get(flavorName);
                     if(nflavor==null) {
                     	nflavor=1;
                     	flavorDay.put(flavorName,nflavor);
                     }
                     else {
                     	//同一种虚拟机数量+1
                     	flavorDay.put(flavorName,++nflavor);
                     }
                     flavorAll.put(dataMark,new TreeMap<String,Integer>(flavorDay));
                }
                if(!dataMark.equals(today)) {
                	 flavorDay.clear();
                	 Integer nflavor=flavorDay.get(flavorName);
                     if(nflavor==null) {
                     	nflavor=1;
                     	flavorDay.put(flavorName,nflavor);
                     }
                     else {
                     	//同一种虚拟机数量+1
                     	flavorDay.put(flavorName,++nflavor);
                     }
                	 dataMark=today;
                }
                
			}
		}
		return flavorAll;
	}
     
}
